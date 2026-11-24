package com.chifaa.backend.chat;
import org.springframework.stereotype.Service;

@Service
public class LanguageDetectionService {
    String detect(String text) {
        boolean arabic = text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.ARABIC);
        boolean latin = text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN);
        if (arabic && latin) return "Mixed";
        if (text.matches("(?s).*(?:شنو|برشا|بزاف|نحب|ما نجم).*")) return "Darija";
        return arabic ? "MSA" : "French";
    }
}
