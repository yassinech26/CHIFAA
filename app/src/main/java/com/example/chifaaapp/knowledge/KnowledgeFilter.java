package com.example.chifaaapp.knowledge;

import java.util.Locale;

final class KnowledgeFilter {
    final String keyword, topic, language;

    KnowledgeFilter(String keyword, String topic, String language) {
        this.keyword = keyword.trim();
        this.topic = topic.trim();
        this.language = language;
    }

    boolean matches(KnowledgeApi.Item item) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        return (language.isEmpty() || language.equals(item.language))
                && (topic.isEmpty() || topic.equalsIgnoreCase(item.topic))
                && (item.title.toLowerCase(Locale.ROOT).contains(needle)
                    || item.contentText.toLowerCase(Locale.ROOT).contains(needle)
                    || item.topic.toLowerCase(Locale.ROOT).contains(needle));
    }
}
