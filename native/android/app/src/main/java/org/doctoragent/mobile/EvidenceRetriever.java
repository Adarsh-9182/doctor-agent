package org.doctoragent.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Lexical topic routing plus field-weighted BM25. Scores are not medical confidence. */
final class EvidenceRetriever {
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            ("a about and are be can could do does for give help how i in is it me my of on please should tell "
            + "the to what when where which why with you your information general read mean explain "
            + "ke ki ka baare mein mujhe batao hai hain kya kaise").split(" ")));
    private static final Pattern DISTRACTORS = Pattern.compile(
            "\\b(?:rest api|active listening|active directory|license plate|licence plate|physical server|"
            + "sleep mode|sleep function|water pump|food for thought|balanced tree|safe password)\\b");
    private static final Set<String> VAGUE = new HashSet<>(Arrays.asList(
            "health", "healthy", "wellbeing", "wellness", "rest", "tired", "fatigue", "food", "active"));
    private static final double K1 = 1.2, B = .75;

    static final class Match {
        final EducationEngine.Source source;
        final double score;
        final int firstMention;
        final List<String> matchedTopics;
        Match(EducationEngine.Source source, double score, List<String> matchedTopics, int firstMention) {
            this.source = source; this.score = score;
            this.firstMention = firstMention;
            this.matchedTopics = Collections.unmodifiableList(new ArrayList<>(matchedTopics));
        }
    }

    static final class Result {
        final List<Match> matches;
        final boolean needsClarification;
        Result(List<Match> matches, boolean needsClarification) {
            this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
            this.needsClarification = needsClarification;
        }
    }

    private static final class Document {
        final EducationEngine.Source source;
        final List<Map<String, Integer>> fields = new ArrayList<>();
        final Set<String> terms = new HashSet<>();
        Document(EducationEngine.Source source) {
            this.source = source;
            fields.add(frequencies(source.title));
            fields.add(frequencies(String.join(" ", source.keywords)));
            fields.add(frequencies(source.text));
            for (Map<String, Integer> field : fields) terms.addAll(field.keySet());
        }
    }

    static Result search(String question, List<EducationEngine.Source> catalog) {
        String normalized = normalize(question);
        String routed = DISTRACTORS.matcher(normalized).replaceAll(" ").trim().replaceAll("\\s+", " ");
        boolean removedDistractor = !routed.equals(normalized);
        Set<String> query = new LinkedHashSet<>(tokens(routed));
        ArrayList<Document> documents = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        double[] averageLengths = new double[3];
        for (EducationEngine.Source source : catalog) {
            Document doc = new Document(source); documents.add(doc);
            for (String term : doc.terms) documentFrequency.merge(term, 1, Integer::sum);
            for (int field = 0; field < 3; field++) averageLengths[field] += length(doc.fields.get(field));
        }
        for (int field = 0; field < 3; field++) averageLengths[field] /= Math.max(1, documents.size());
        ArrayList<Match> candidates = new ArrayList<>();
        double[] weights = {3.0, 1.5, .5};
        for (Document doc : documents) {
            // Body overlap can rank an already supported topic, never activate one by itself.
            ArrayList<String> topics = new ArrayList<>();
            int firstMention = Integer.MAX_VALUE;
            for (String term : doc.source.retrievalTerms) {
                String topic = normalize(term);
                int position = (" " + routed + " ").indexOf(" " + topic + " ");
                if (!topic.isEmpty() && position >= 0 && !topics.contains(topic)) {
                    topics.add(topic); firstMention = Math.min(firstMention, position);
                }
            }
            if (topics.isEmpty()) continue;
            double score = 0;
            for (String term : query) {
                int df = documentFrequency.getOrDefault(term, 0);
                if (df == 0) continue;
                double idf = Math.log(1 + (documents.size() - df + .5) / (df + .5));
                for (int field = 0; field < 3; field++) {
                    int tf = doc.fields.get(field).getOrDefault(term, 0);
                    if (tf == 0) continue;
                    double norm = (1 - B) + B * length(doc.fields.get(field)) / Math.max(1, averageLengths[field]);
                    score += weights[field] * idf * (tf * (K1 + 1)) / (tf + K1 * norm);
                }
            }
            // Curated route terms may be synonyms absent from the excerpt's wording.
            if (score == 0) score = .01;
            candidates.add(new Match(doc.source, score, topics, firstMention));
        }
        candidates.sort((a, b) -> {
            // Answer explicitly requested topics in mention order; rank competing sources with BM25.
            int order = Integer.compare(a.firstMention, b.firstMention);
            if (order == 0) order = Double.compare(b.score, a.score);
            return order == 0 ? a.source.id.compareTo(b.source.id) : order;
        });
        if (candidates.size() > 3) candidates = new ArrayList<>(candidates.subList(0, 3));
        boolean vague = false;
        for (String token : query) if (VAGUE.contains(token)) vague = true;
        return new Result(candidates, candidates.isEmpty() && vague && !removedDistractor);
    }

    static String normalize(String value) {
        String aliased = LocalLanguage.expandTopicAliases(value == null ? "" : value);
        String english = aliased.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim().replaceAll("\\s+", " ");
        StringBuilder result = new StringBuilder();
        for (String token : english.split(" ")) {
            switch (token) {
                case "sleeping": case "asleep": token = "sleep"; break;
                case "hydrating": case "hydrated": token = "hydration"; break;
                case "refrigerating": case "refrigeration": token = "refrigerate"; break;
                default: break;
            }
            if (!token.isEmpty()) { if (result.length() > 0) result.append(' '); result.append(token); }
        }
        return result.toString();
    }

    private static List<String> tokens(String value) {
        ArrayList<String> tokens = new ArrayList<>();
        for (String term : normalize(value).split(" ")) if (term.length() >= 3 && !STOP.contains(term)) tokens.add(term);
        return tokens;
    }

    private static Map<String, Integer> frequencies(String value) {
        Map<String, Integer> counts = new HashMap<>();
        for (String term : tokens(value)) counts.merge(term, 1, Integer::sum);
        return counts;
    }

    private static int length(Map<String, Integer> field) {
        int length = 0;
        for (int count : field.values()) length += count;
        return length;
    }
}
