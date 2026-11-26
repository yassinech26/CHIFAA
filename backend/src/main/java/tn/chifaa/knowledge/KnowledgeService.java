package tn.chifaa.knowledge;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.chifaa.users.PatientAccess;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
class KnowledgeService {
    private final KnowledgeRepository repository;

    private final PatientAccess access;

    KnowledgeService(KnowledgeRepository repository, PatientAccess access) {
        this.access = access;
        this.repository = repository;
    }

    ItemPage search(UUID user, String q, String topic, String language, String after, int limit) {
        access.lock(user);
        List<ItemSummary> rows = repository.search(q.strip(), topic.strip(), language, after,
                PageRequest.of(0, limit + 1));
        if (rows.size() <= limit) {
            return new ItemPage(rows, null);
        }
        return new ItemPage(rows.subList(0, limit), rows.get(limit - 1).contentId());
    }

    Optional<ItemDetail> find(UUID user, String id) {
        access.lock(user);
        return repository.findByContentId(id);
    }
}
