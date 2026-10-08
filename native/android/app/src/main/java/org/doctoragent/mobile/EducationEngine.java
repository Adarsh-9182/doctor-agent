package org.doctoragent.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Source-only education logic. No Android APIs, network calls, or storage. */
public final class EducationEngine {
    private static final Pattern URGENT = Pattern.compile("\\b(chest pain|can't breathe|cannot breathe|difficulty breathing|trouble breathing|shortness of breath|face droop|one-sided weakness|severe bleeding|heavy bleeding|suicid\\w*|want to die|kill myself|end my life|hurt myself|self[- ]harm|overdose)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDICINE = Pattern.compile("\\b(diagnos\\w*|prescrib\\w*|dose|dosage|how many (pills|tablets)|should i take|should i stop|should i start)\\b", Pattern.CASE_INSENSITIVE);

    public static final class Source {
        public final String id, title, text;
        public final List<String> keywords, retrievalTerms;
        public Source(String id, String title, List<String> keywords, String text) {
            this(id, title, keywords, text, Collections.singletonList(title));
        }
        public Source(String id, String title, List<String> keywords, String text, List<String> retrievalTerms) {
            this.id = id; this.title = title; this.text = text;
            this.keywords = Collections.unmodifiableList(new ArrayList<>(keywords));
            this.retrievalTerms = Collections.unmodifiableList(new ArrayList<>(retrievalTerms));
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
        if (question == null) question = "";
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
        EvidenceRetriever.Result result = EvidenceRetriever.search(question, catalog);
        if (result.matches.isEmpty()) {
            if (result.needsClarification) return new Answer(
                    "Which topic do you mean: sleep, nutrition, hydration, food safety, or movement? The words in your question don't identify a supported topic clearly enough. I can't assess symptoms from this library.",
                    "clarification", Collections.emptyList());
            return new Answer("I don’t have a suitable source for that topic in my small library yet. Try a general question about nutrition, sleep, hydration, food safety, or physical activity, or ask a qualified healthcare professional.", "not-covered", Collections.emptyList());
        }
        StringBuilder text = new StringBuilder("Here are the bundled summaries for the topics I matched. These summaries may not answer every detail of your question:\n\n");
        List<String> ids = new ArrayList<>();
        for (EvidenceRetriever.Match entry : result.matches) {
            ids.add(entry.source.id);
            text.append(entry.source.title).append(": ").append(entry.source.text).append("\n\n");
        }
        text.append("This is general information, not a personal diagnosis or care plan.");
        return new Answer(text.toString(), "reference-only", ids);
    }

}
