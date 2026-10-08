package org.doctoragent.mobile;

import java.util.Locale;

/** Recognizes explicit conversational phrases, without guessing medical intent. */
final class ConversationContext {
    static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").trim().replaceAll(" +", " ");
    }

    static boolean isFollowUp(String value) {
        return normalize(value).matches("tell me more|explain that|explain more|go on|continue|"
                + "what do you mean|can you explain that|why is that|what does that mean|"
                + "what does it mean|explain it simply|explain that simply|"
                + "how does that work|more about that|what about that|"
                + "can you give an example|give me an example|any examples");
    }

    static boolean needsTopic(String value) {
        return normalize(value).matches("help|help me|i need help|can you help me|"
                + "what should i do|how can i improve|how can i improve my health|"
                + "i want to feel better|where do i start|what can you do");
    }
}
