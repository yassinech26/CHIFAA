package tn.chifaa.chat;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
class KnowledgeSearchService {
    record Hit(String id, String topic, String content, String notice) {}
    private final JdbcClient db;
    KnowledgeSearchService(JdbcClient db) { this.db = db; }

    Optional<Hit> search(String question, String language) {
        List<String> words = ChatText.keywords(question);
        if (words.isEmpty()) return Optional.empty();
        String preferred = ChatText.preferredContentLanguage(question,language);
        return db.sql("""
                select v.content_id, v.topic, v.content_text, v.notice
                from kb_visible_items v
                cross join lateral (
                    select sum(case when w=any(regexp_split_to_array(lower(v.topic), '[[:space:][:punct:]]+')) then 5
                                    when w=any(regexp_split_to_array(lower(v.title), '[[:space:][:punct:]]+')) then 3
                                    when w=any(regexp_split_to_array(lower(v.content_text), '[[:space:][:punct:]]+')) then 1
                                    else 0 end) as score
                    from unnest(string_to_array(:words,' ')) tokens(w)
                ) ranked
                where (v.language=:preferred or (:lang='Mixed' and v.language='French')) and ranked.score > 0
                order by (v.language=:preferred) desc, ranked.score desc, v.content_id
                limit 1
                """).param("words",String.join(" ",words)).param("preferred",preferred).param("lang",language)
                .query((r,n) -> new Hit(r.getString(1),r.getString(2),r.getString(3),r.getString(4))).optional();
    }
}
