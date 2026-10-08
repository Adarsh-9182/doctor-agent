package org.doctoragent.mobile;

/** User-authored visit preparation. No inference, diagnosis, model call or persistence. */
final class VisitNotes {
    static final String[] LABELS = {"Main concern / reason for visit", "When it started and how it changes",
            "What makes it better or worse", "How it affects your daily life",
            "Known conditions, current medicines or allergies (optional)", "Questions you want to ask"};
    static final int LIMIT = 600;

    static final class Draft {
        final String[] values = new String[LABELS.length];
        Draft() { clear(); }
        void clear() { java.util.Arrays.fill(values, ""); }
        Draft copy() {
            Draft next = new Draft(); System.arraycopy(values, 0, next.values, 0, values.length); return next;
        }
        String raw() { return String.join("\n", values); }
    }

    static String format(Draft draft) {
        if (draft.values[0].trim().isEmpty()) throw new IllegalArgumentException("Enter a main concern or reason for the visit.");
        StringBuilder result = new StringBuilder("USER-ENTERED VISIT NOTES\nThese notes do not assess urgency or establish a diagnosis.\n");
        for (int i = 0; i < LABELS.length; i++) {
            if (draft.values[i].length() > LIMIT) throw new IllegalArgumentException("Keep each field within 600 characters.");
            String value = draft.values[i].trim();
            result.append('\n').append(LABELS[i]).append(":\n").append(value.isEmpty() ? "Not entered" : value).append('\n');
        }
        return result.toString();
    }

    static String questions() {
        return "Questions you can choose to ask your clinician:\n\n"
                + "• What could explain my concern, and what else do you need to know?\n"
                + "• Which changes should prompt urgent help?\n"
                + "• Do I need tests, and what would their results tell us?\n"
                + "• What are the next steps and when should I follow up?\n\n"
                + "Your notes above contain only what you entered; missing details remain marked as not entered. "
                + "This preparation does not establish whether it is safe to wait for an appointment.";
    }
}
