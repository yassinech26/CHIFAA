package com.chifaa.backend.knowledge;

import com.chifaa.backend.compatibility.KnowledgeSearchProvider;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService implements KnowledgeSearchProvider {
    private static final String ADMISSIBLE = """
        SELECT content_id AS id,title,body,source,reviewed_date,fictional,version FROM kb_items
        WHERE status = 'Approved' AND (expires_on IS NULL OR expires_on >= (now() AT TIME ZONE 'UTC')::date)
        """;
    private final JdbcClient jdbc;

    public KnowledgeSearchService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Reference> search(String topic, String language, String normalizedQuery) {
        String words = String.join(" ", Arrays.stream(normalizedQuery.split(" "))
                .filter(token -> token.length() >= 4).distinct().limit(8).toList());
        if (topic.isEmpty() && !words.contains(" ")) return Optional.empty();
        return jdbc.sql(ADMISSIBLE + """
              AND (language = :language OR :language = 'Mixed')
              AND ((:topic <> '' AND topic = :topic)
                  OR (:topic = '' AND to_tsvector('simple', translate(lower(title || ' ' || topic || ' ' || body),
                      'àâäçéèêëîïôöùûüÿ','aaaceeeeiioouuuy')) @@ plainto_tsquery('simple', :words)))
              ORDER BY CASE WHEN language = 'French' THEN 0 WHEN language = 'MSA' THEN 1 ELSE 2 END,
                reviewed_date DESC NULLS LAST, content_id LIMIT 1
              """).param("language",language).param("topic",topic).param("words",words).query(Reference.class).optional();
    }

    @Override
    public Optional<Reference> find(UUID id) {
        return jdbc.sql(ADMISSIBLE + " AND content_id = ?").param(id).query(Reference.class).optional();
    }
}
