package tn.chifaa.chat;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

final class ChatText {
    private ChatText() {}
    private static final Pattern ARABIC = Pattern.compile("[\\p{IsArabic}]");
    private static final Pattern LATIN = Pattern.compile("[\\p{IsLatin}]");
    private static final Set<String> DARIJA = Set.of("شنوة","شنو","شنية","نحب","نجم","قداش","برشا","علاش","كيفاش",
            "بغيت","شنوّة","chneya","chnowa","chnoua","nheb","najjem","kifech","kifeh","9adech","barcha","3lech","bghit");
    private static final Set<String> FRENCH = Set.of("je","le","la","les","des","mon","mes","avec","bonjour","cherche","voudrais");
    private static final Set<String> STOP = Set.of("les","des","une","pour","avec","mon","mes","que","qui","est","sur","dans",
            "vous","suis","sont","aux","svp","peux","veux","cherche","voudrais","puis","moi","bonjour","avoir","fiche","fiches",
            "information","informations","besoin","comment","quel","quelle","quels","quelles","هذا","هذه","على","عن","اريد","أريد","معلومات","حول");
    private static final Pattern MEDICAL = Pattern.compile(
            "(?U)\\b(diagnos\\w*|posolog\\w*|dosages?|doses?|milligrammes?|comprim[eé]s?|medicaments?|prescri\\w*|ordonnance|"
            + "combien (de|dois|puis)|quelle quantite|suis je (atteinte?|malade)|ai je (un|une|le|la)|est ce (un|une|le|la) cancer|"
            + "est ce que j ai|savoir si j ai|mes symptomes|quel traitement|dois je prendre|combien prendre|"
            + "تشخيص\\w*|التشخيص|جرع\\w*|الجرع\\w*|دواء|الدواء|حبوب|كم حبة|كم اخذ|"
            + "هل (عندي|لدي|هذا سرطان|انا مصاب\\w*)|واش عندي|قداش ناخذ|شنوة عندي|9adech nekhou|chneya andi|chnowa andi|mg)\\b");

    static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
                .replaceAll("[\\p{M}ـ]", "").replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    static String language(String text) {
        String normalized = normalize(text);
        List<String> words = Arrays.asList(normalized.split(" "));
        boolean arabic = ARABIC.matcher(normalized).find();
        boolean latin = LATIN.matcher(normalized).find();
        boolean dialect = words.stream().anyMatch(DARIJA::contains);
        if ((arabic && latin) || (dialect && words.stream().anyMatch(FRENCH::contains))) return "Mixed";
        return dialect ? "Darija" : arabic ? "MSA" : "French";
    }

    static boolean arabicReply(String text, String lang) {
        return lang.equals("Darija") || lang.equals("MSA") || (lang.equals("Mixed") && ARABIC.matcher(text).find());
    }

    static String preferredContentLanguage(String text, String language) {
        if (!language.equals("Mixed")) return language;
        for (String word : normalize(text).split(" ")) {
            if (DARIJA.contains(word)) return "Darija";
        }
        return "MSA";
    }

    static boolean medical(String normalized) { return MEDICAL.matcher(normalized).find(); }

    static Optional<ChatPolicy.Trigger> distress(String normalized, ChatPolicy policy) {
        String padded = " " + normalized + " ";
        return policy.triggers().stream()
                .filter(t -> padded.contains(" " + normalize(t.phrase()) + " "))
                .max(Comparator.comparing(ChatPolicy.Trigger::severity));
    }

    static List<String> keywords(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> w.length() >= 2 && !STOP.contains(w) && !FRENCH.contains(w) && !DARIJA.contains(w))
                .filter(w -> w.length() >= 3 || ARABIC.matcher(w).find())
                .distinct().limit(32).toList();
    }
}
