package com.chifaa.backend.chat;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TopicDetectionService {
    public static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    String topic(String message) {
        if (message.matches(".*(?:rendez vous|consultation|موعد).*")) return "APPOINTMENT";
        if (message.matches(".*(?:transport|نقل).*")) return "TRANSPORT";
        return "";
    }
}
