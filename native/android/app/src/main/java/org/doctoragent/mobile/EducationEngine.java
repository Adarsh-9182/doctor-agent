package org.doctoragent.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Source-only education logic. No Android APIs, network calls, or storage. */
public final class EducationEngine {
    private static final Set<String> STOP = new HashSet<>(Arrays.asList("a about and are can could do does for give help how i in is it me my of on please should tell the to what when where which why with you your information general read mean explain".split(" ")));
    private static final Pattern URGENT = Pattern.compile("\\b(chest pain|can't breathe|cannot breathe|difficulty breathing|trouble breathing|shortness of breath|face droop|one-sided weakness|severe bleeding|heavy bleeding|suicid\\w*|want to die|kill myself|end my life|hurt myself|self[- ]harm|overdose)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDICINE = Pattern.compile("\\b(diagnos\\w*|prescrib\\w*|dose|dosage|how many (pills|tablets)|should i take|should i stop|should i start)\\b", Pattern.CASE_INSENSITIVE);

    public static final class Source {
        public final String id, title, text;
        public final List<String> keywords;
        public Source(String id, String title, List<String> keywords, String text) {
            this.id = id; this.title = title; this.keywords = keywords; this.text = text;
        }
    }

    public static final class Answer {
        public final String text, mode;
        public final List<String> sourceIds;
        private Answer(String text, String mode, List<String> sourceIds) {
            this.text = text; this.mode = mode;
            this.sourceIds = Collections.unmodifiableList(new ArrayList<>(sourceIds));
        }
    }

    public static Answer answer(String question, List<Source> catalog) {
        if (URGENT.matcher(question).find() || LocalLanguage.hasUrgentSignal(question)) {
            String message = LocalLanguage.isHindi(question) ? LocalLanguage.urgentMessage(question)
                    : "This could need urgent, in-person help. Contact your local emergency services or crisis line now, or ask someone nearby to help you. I can’t assess emergencies in chat.";
            return new Answer(message, "urgent-care", Collections.emptyList());
        }
        if (MEDICINE.matcher(question).find() || LocalLanguage.hasMedicationRequest(question)) {
            String message = LocalLanguage.isHindi(question) ? LocalLanguage.medicationMessage(question)
                    : "I can’t diagnose, prescribe, or recommend starting, stopping, or changing a medicine. A qualified healthcare professional or pharmacist can advise you about your situation. I can help you prepare questions to ask them.";
            return new Answer(message, "professional-care", Collections.emptyList());
        }
        Set<String> query = new HashSet<>(tokens(question));
        query.removeAll(STOP);
        ArrayList<Ranked> ranked = new ArrayList<>();
        for (Source source : catalog) {
            Set<String> title = new HashSet<>(tokens(source.title));
            Set<String> keywords = new HashSet<>();
            for (String keyword : source.keywords) keywords.addAll(tokens(keyword));
            Set<String> body = new HashSet<>(tokens(source.text));
            int score = 0;
            for (String token : query) score += title.contains(token) ? 4 : keywords.contains(token) ? 2 : body.contains(token) ? 1 : 0;
            if (score > 0) ranked.add(new Ranked(source, score));
        }
        ranked.sort((a, b) -> a.score == b.score ? a.source.id.compareTo(b.source.id) : Integer.compare(b.score, a.score));
        if (ranked.isEmpty()) {
            return new Answer("I don’t have a suitable source for that topic in my small library yet. Try a general question about nutrition, sleep, hydration, food safety, or physical activity, or ask a qualified healthcare professional.", "not-covered", Collections.emptyList());
        }
        int cutoff = Math.max(1, (ranked.get(0).score + 1) / 2);
        StringBuilder text = new StringBuilder("Here’s what my sources say:\n\n");
        List<String> ids = new ArrayList<>();
        for (Ranked entry : ranked) {
            if (entry.score < cutoff || ids.size() >= 3) continue;
            ids.add(entry.source.id);
            text.append(entry.source.title).append(": ").append(entry.source.text).append("\n\n");
        }
        text.append("This is general information, not a personal diagnosis or care plan.");
        return new Answer(text.toString(), "reference-only", ids);
    }

    private static List<String> tokens(String value) {
        List<String> out = new ArrayList<>();
        for (String token : LocalLanguage.expandTopicAliases(value).toLowerCase(Locale.ROOT).split("[^a-z]+")) if (token.length() >= 3) out.add(token);
        return out;
    }

    private static final class Ranked {
        final Source source; final int score;
        Ranked(Source source, int score) { this.source = source; this.score = score; }
    }
}
