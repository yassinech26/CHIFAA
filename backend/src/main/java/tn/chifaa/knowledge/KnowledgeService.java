package tn.chifaa.knowledge;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
class KnowledgeService {
    private final KnowledgeRepository repository;

    KnowledgeService(KnowledgeRepository repository) {
        this.repository = repository;
    }

    ItemPage search(String q, String topic, String language, String after, int limit) {
        List<ItemSummary> rows = repository.search(q.strip(), topic.strip(), language, after,
                PageRequest.of(0, limit + 1));
        if (rows.size() <= limit) {
            return new ItemPage(rows, null);
        }
        return new ItemPage(rows.subList(0, limit), rows.get(limit - 1).contentId());
    }

    Optional<ItemDetail> find(String id) {
        return repository.findByContentId(id);
    }
}
