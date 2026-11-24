package com.chifaa.backend.compatibility;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
public interface KnowledgeSearchProvider {
    record Reference(UUID id, String title, String body, String source, LocalDate reviewedDate, boolean fictional, int version) {}
    Optional<Reference> search(String topic, String language, String normalizedQuery);
    Optional<Reference> find(UUID id);
}
