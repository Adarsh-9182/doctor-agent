package org.doctoragent.mobile;

import java.util.Locale;
import java.util.regex.Pattern;

/** Bounded Hindi/Hinglish intent mapping and safety copy; never translates with the model. */
final class LocalLanguage {
    private static final Pattern DEVANAGARI = Pattern.compile("[\\u0900-\\u097F]");
    private static final Pattern HINGLISH = Pattern.compile(
            "(?:^|\\b)(mujhe|mujhko|mera|meri|mere|mujh|kya|kaise|kyu|kyun|hai|hain|hoga|"
            + "karu|karo|karna|karni|chahiye|nahi|nahin|paani|pani|neend|nind|batao|samjhao|"
            + "seene|chati|chaati|saans|dawai|dava|goli|khana|khaana|poshan|vyayam|aatmahatya|khudkushi|"
            + "santulit|bhojan|aahar|khanapan|sharirik|kasrat|namaste|namaskar|dhanyavad|dhanyavaad|shukriya|kripya|achha|accha)(?:\\b|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDICINE_HI = Pattern.compile(
            "(?:\\b(?:dawai|dava|medicine|goli|tablet|insulin|supplement|dose|dosage|mg|mcg)\\b.{0,55}"
            + "\\b(?:kitna|kitni|kab|lena|leni|lu|loo|band|rok|shuru|start|stop|continue|chahiye|change)\\b"
            + "|\\b(?:kitna|kitni)\\b.{0,35}\\b(?:dawai|dava|medicine|goli|tablet|dose|dosage|insulin|supplement)\\b"
            + "|\\b(?:start|stop|change|continue)\\b.{0,35}\\b(?:dawai|dava|medicine|goli|tablet|insulin)\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HINGLISH_MEDICINE = Pattern.compile(
            "\\b(?:kitna|kitni|leni|lena|lu|loo)\\b.{0,35}\\b(?:dawai|dava|medicine|goli|tablet|dose|dosage|insulin|supplement)\\b",
            Pattern.CASE_INSENSITIVE);

    private LocalLanguage() {}

    static boolean isHindi(String text) {
        if (text == null) return false;
        String value = normalized(text);
        boolean medicationQuestion = HINGLISH_MEDICINE.matcher(value).find();
        return DEVANAGARI.matcher(text).find() || HINGLISH.matcher(text).find() || medicationQuestion;
    }

    static boolean isDevanagari(String text) {
        return text != null && DEVANAGARI.matcher(text).find();
    }

    static boolean isHindiRoman(String text) {
        return text != null && !DEVANAGARI.matcher(text).find() && HINGLISH.matcher(text).find();
    }

    static boolean isFollowUp(String text) {
        String value = normalized(text);
        return containsAny(value, "thoda aur batao", "thoda aur samjhao", "thoda vistaar se",
                "aur samjhao", "iska kya matlab",
                "इसका क्या मतलब", "इसका मतलब", "इसका अर्थ", "kyun aisa hai", "ऐसा क्यों",
                "example do", "misal do", "give example", "iska example", "और बताओ", "थोड़ा और बताओ",
                "थोड़ा विस्तार से", "उदाहरण दो");
    }

    static boolean needsTopic(String text) {
        String value = normalized(text);
        return containsAny(value, "क्या करूं", "kya karu", "क्या करूँ", "kahan se shuru",
                "कहाँ से शुरू", "कहां से शुरू", "मदद करो", "meri madad karo", "मेरी मदद करो",
                "स्वास्थ्य कैसे सुधारूं", "sehat kaise sudharu", "बेहतर कैसे महसूस करूं");
    }

    static boolean isGreeting(String text) {
        String value = normalized(text);
        return value.matches("hi|hello|hey|namaste|namaskar|good morning|good evening|shukriya|dhanyavad|dhanyavaad|thanks|thank you")
                || containsAny(value, "नमस्ते", "नमस्कार", "हाय", "हैलो", "शुक्रिया", "धन्यवाद");
    }

    static boolean isThanks(String text) {
        String value = normalized(text);
        return value.matches("thanks|thank you|shukriya|dhanyavad|dhanyavaad")
                || containsAny(value, "शुक्रिया", "धन्यवाद");
    }

    static String askForTopicMessage(String text) {
        return isDevanagari(text)
                ? "आप किस विषय पर बात करना चाहते हैं: नींद, पोषण, पानी, खाने की सुरक्षा, या शारीरिक गतिविधि? इनमें से कोई एक चुनें।"
                : "Aap kis topic par baat karna chahte hain: neend, poshan, paani, khaane ki suraksha, ya sharirik gatividhi? Inmein se koi ek chun sakte hain.";
    }

    static boolean hasUrgentSignal(String text) {
        String value = normalized(text);
        return containsAny(value,
                "सीने में दर्द", "छाती में दर्द", "सांस नहीं आ रही", "साँस नहीं आ रही",
                "सांस लेने में दिक्कत", "सांस लेने में तकलीफ", "सांस फूल रही", "चेहरा टेढ़ा",
                "मुंह टेढ़ा", "मुंह एक तरफ", "एक तरफ कमजोरी", "एक हाथ कमजोर", "एक हाथ कमज़ोर", "बहुत खून बह",
                "खुदकुशी", "आत्महत्या", "खुद को मार", "अपनी जान लेना", "मरना चाहता",
                "मार जाना चाहता", "दवा की अधिक मात्रा", "ज्यादा गोली खा", "ज्यादा दवा खा",
                "seene mein dard", "seene me dard", "chhati mein dard", "chati me dard",
                "saans nahi aa", "saans lene mein dikkat", "saans lene mein takleef", "saans phool rahi",
                "saans lene me dikkat", "muh tedha", "chehra tedha", "ek taraf kamzori",
                "ek haath kamzor", "ek haath mein kamzori", "ek side kamzor", "ek side kamzori", "face latak", "chehra latak",
                "bahut khoon beh", "bohot khoon beh", "zyada khoon beh", "khudkushi",
                "aatmahatya", "khud ko maar", "apni jaan lena", "marna chahta",
                "marna chahti", "jeene ka mann nahi", "jeene ka man nahi", "jeene ka man nahin",
                "jeene ka mann nahi hai", "jeene ka man nahi hai", "mar jaana chahta", "mar jana chahta",
                "zyada goli kha", "zyada dawa kha", "galti se zyada dawa",
                "galti se zyada goli", "galti se extra tablet", "galti se extra goli", "extra goli le li");
    }

    static boolean hasMedicationRequest(String text) {
        if (text == null) return false;
        String value = normalized(text);
        if (MEDICINE_HI.matcher(value).find()) return true;
        return containsAny(value,
                "दवा कितनी लूं", "दवा कितनी लू", "दवा की कितनी मात्रा", "गोली कितनी लूं",
                "दवा कितनी", "दवाई कितनी", "कितनी दवा", "कितनी दवाई", "गोली कितनी", "कितनी गोली",
                "दवाई बंद करूं", "दवा बंद करूं", "दवा शुरू करूं", "दवाई शुरू करूं", "दवा कितनी लूं", "दवाई कितनी लूं",
                "दवाई बंद कर", "दवा बंद कर", "दवा शुरू कर", "दवाई शुरू कर", "दवा चालू कर",
                "कौन सी दवा लूं", "कौनसी दवा लूं", "मुझे दवा बताओ", "मुझे दवाई बताओ",
                "दवा बदल दूं", "दवाई बदल दूं", "दवा लेनी चाहिए", "दवाई लेनी चाहिए",
                "दवा लेना चाहिए", "खुराक कितनी");
    }

    static String expandTopicAliases(String input) {
        if (input == null) return "";
        String value = input.toLowerCase(Locale.ROOT);
        // Recognize food-safety phrasing first, so "khana safe" stays on that topic.
        value = value.replace("खाने की सुरक्षा", " food safety ").replace("खाद्य सुरक्षा", " food safety ")
                .replace("खाने को सुरक्षित", " food safety ").replace("खाने की safety", " food safety ")
                .replace("khaane ko surakshit", " food safety ").replace("khane ko surakshit", " food safety ")
                .replace("khaane ki suraksha", " food safety ").replace("khane ki suraksha", " food safety ")
                .replace("खाना स्टोर", " food safety ")
                .replace("खाना सुरक्षित", " food safety ").replace("खाना safe", " food safety ")
                .replace("khana safe", " food safety ").replace("khaana safe", " food safety ")
                .replace("khana surakshit", " food safety ").replace("khaana surakshit", " food safety ")
                .replace("khana store", " food safety ").replace("khaana store", " food safety ")
                .replace("khana pakana", " food safety ").replace("khaana pakana", " food safety ")
                .replace("khadya suraksha", " food safety ")
                .replace("khane ka bhandaran", " food storage ")
                .replace("sharirik gatividhi", " physical activity ")
                .replace("food safety", " food safety ");
        String[][] aliases = {
                {"नींद", "sleep"}, {"सोना", "sleep"}, {"neend", "sleep"}, {"nind", "sleep"}, {"sone", "sleep"},
                {"पोषण", "nutrition"}, {"खानपान", "nutrition"}, {"sehatmand khana", "healthy eating"},
                {"healthy khana", "healthy eating"}, {"poshan", "nutrition"}, {"khanapan", "nutrition"},
                {"संतुलित", "balanced"}, {"santulit", "balanced"}, {"भोजन", "meal"},
                {"aahar", "nutrition"}, {"आहार", "nutrition"},
                {"पानी", "water"}, {"जल", "water"}, {"प्यास", "thirst"}, {"paani", "water"},
                {"pani", "water"}, {"pyas", "thirst"}, {"pyaas", "thirst"},
                {"व्यायाम", "exercise"}, {"शारीरिक गतिविधि", "physical activity"}, {"एक्सरसाइज", "exercise"},
                {"vyayam", "exercise"}, {"exercise", "exercise"},
                {"खाना", "nutrition"}, {"खाने", "nutrition"}, {"khaana", "nutrition"},
                {"khana", "nutrition"}, {"khaane", "nutrition"}, {"bhojan", "meal"}
        };
        for (String[] alias : aliases) {
            if (alias[0].matches(".*[a-z].*"))
                value = value.replaceAll("(?i)\\b" + Pattern.quote(alias[0]) + "\\b", " " + alias[1] + " ");
            else value = value.replace(alias[0], " " + alias[1] + " ");
        }
        return value;
    }

    static String urgentMessage(String question) {
        boolean india = "IN".equalsIgnoreCase(Locale.getDefault().getCountry());
        boolean devanagari = isDevanagari(question);
        String emergency = india ? "Abhi 112 par call karo ya paas ke kisi bharosemand vyakti se turant madad maango. "
                : "Abhi apne local emergency number par call karo ya paas ke kisi bharosemand vyakti se madad maango. ";
        String value = normalized(question);
        boolean selfHarm = value.contains("suicid") || containsAny(value, "खुदकुशी", "आत्महत्या", "खुद को मार", "अपनी जान लेना",
                "khudkushi", "aatmahatya", "khud ko maar", "apni jaan lena", "marna chahta", "marna chahti");
        if (selfHarm) {
            if (devanagari) {
                String indiaHelp = india ? " भारत में Tele-MANAS 14416 पर भी कॉल कर सकते हैं।" : "";
                return "मुझे अफ़सोस है कि आप इस मुश्किल से गुज़र रहे हैं। "
                        + (india ? "अभी 112 पर कॉल करें। " : "अभी अपने स्थानीय आपातकालीन नंबर पर कॉल करें। ")
                        + "अगर हो सके तो किसी भरोसेमंद व्यक्ति के पास जाएँ और अकेले न रहें।"
                        + indiaHelp + " मैं चैट से तुरंत मदद नहीं भेज सकता।";
            }
            String helpline = india ? " Bharat mein Tele-MANAS 14416 par bhi call kar sakte ho." : "";
            return "Mujhe afsos hai ki tum is mushkil mein ho. " + emergency
                    + "Agar ho sake to abhi kisi bharosemand vyakti ke paas jao aur akele mat raho."
                    + helpline + " Main chat mein turant madad nahi bhej sakta.";
        }
        if (devanagari) return "यह आपात स्थिति हो सकती है। "
                + (india ? "अभी 112 पर कॉल करें" : "अभी अपने स्थानीय आपातकालीन नंबर पर कॉल करें")
                + " या पास के किसी भरोसेमंद व्यक्ति से मदद माँगें। चैट आपात स्थिति का आकलन नहीं कर सकती; जवाब का इंतज़ार न करें।";
        return "Yeh emergency ho sakti hai. " + emergency
                + "Chat emergency assess nahi kar sakti; reply ka intezar mat karo.";
    }

    static String medicationMessage() {
        return "Main diagnosis, prescription, ya dawa/supplement ki dose batane ya use shuru, band, ya badalne ki salah nahi de sakta. Apni situation ke liye qualified doctor ya pharmacist se baat karo. Main unse poochhne ke liye sawal taiyaar karne mein madad kar sakta hoon.";
    }

    static String medicationMessage(String question) {
        return isDevanagari(question)
                ? "मैं बीमारी का निदान या पर्चा नहीं दे सकता, और दवा या सप्लीमेंट की खुराक बताने अथवा उसे शुरू, बंद या बदलने की सलाह नहीं दे सकता। अपनी स्थिति के लिए योग्य डॉक्टर या फ़ार्मासिस्ट से बात करें। मैं उनसे पूछने के लिए सवाल तैयार करने में मदद कर सकता हूँ।"
                : medicationMessage();
    }

    static String normalized(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}\\s]", " ").trim().replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String value, String... phrases) {
        for (String phrase : phrases) if (value.contains(phrase)) return true;
        return false;
    }
}
