package tn.chifaa.knowledge;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

interface KnowledgeRepository extends Repository<VisibleItem, String> {
    Optional<ItemDetail> findByContentId(String contentId);

    @Query("""
        select new tn.chifaa.knowledge.ItemSummary(i.contentId, i.title, i.topic, i.language, i.fictional, i.notice)
        from VisibleItem i
        where i.contentId > :after
          and (:language = '' or i.language = :language)
          and (:topic = '' or lower(i.topic) = lower(:topic))
          and (:q = '' or locate(lower(:q), lower(i.title)) > 0
                       or locate(lower(:q), lower(i.contentText)) > 0
                       or locate(lower(:q), lower(i.topic)) > 0)
        order by i.contentId
        """)
    List<ItemSummary> search(String q, String topic, String language, String after, Pageable pageable);
}
