package com.chifaa.backend.directory;

import com.chifaa.backend.compatibility.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class OrganisationService {
    public record Results(List<OrganisationResponse> items, long total, int page, boolean hasNext) {}
    private final OrganisationRepository repository;
    private final OrganisationVerificationService verification;
    private final StaffIdentityProvider staff;
    private final AuditService audit;

    public OrganisationService(OrganisationRepository repository, OrganisationVerificationService verification,
            StaffIdentityProvider staff, AuditService audit) {
        this.repository = repository;
        this.verification = verification;
        this.staff = staff;
        this.audit = audit;
    }

    Results search(String query, String governorate, String category, boolean stale, int page, int size) {
        if (page < 0 || page > 1000 || size < 1 || size > 100 || query.length() > 180) badRequest();
        validateFilter(governorate, DirectoryCatalog.GOVERNORATES);
        validateFilter(category, DirectoryCatalog.SERVICE_TYPES);
        String pattern = query.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var results = repository.search("%" + pattern + "%", governorate, category, stale, verification.cutoff(),
                PageRequest.of(page, size, Sort.by("name", "id")));
        return new Results(results.map(this::response).getContent(), results.getTotalElements(), page, results.hasNext());
    }

    OrganisationResponse get(String id) { return response(find(id)); }

    public Map<String, Long> statistics() {
        staff.requireStaff();
        return Map.of("organisations", repository.count(), "due", repository.countDue(verification.cutoff()));
    }

    @Transactional
    OrganisationResponse save(String id, OrganisationRequest request) {
        UUID actor = staff.requireStaff();
        if (!DirectoryCatalog.SERVICE_TYPES.contains(request.serviceType())
                || !DirectoryCatalog.GOVERNORATES.contains(request.governorate())
                || !DirectoryCatalog.LANGUAGES.containsAll(request.languages())
                || new HashSet<>(request.languages()).size() != request.languages().size()) badRequest();
        var entity = id == null ? new Organisation() : find(id);
        if (id == null) entity.id = "ORG-%03d".formatted(repository.nextNumber());
        entity.name = request.name().strip();
        entity.serviceType = request.serviceType();
        entity.governorate = request.governorate();
        entity.contactPhone = request.contactPhone();
        entity.address = request.address();
        entity.cost = request.cost();
        entity.languages.clear();
        entity.languages.addAll(request.languages());
        entity = repository.save(entity);
        audit.record(actor, id == null ? "DIRECTORY_CREATE" : "DIRECTORY_EDIT", entity.id);
        return response(entity);
    }

    @Transactional
    OrganisationResponse verify(String id, String method) {
        var entity = find(id);
        verification.verify(entity, method);
        audit.record(staff.requireStaff(), "DIRECTORY_VERIFY", id);
        return response(entity);
    }

    private Organisation find(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private OrganisationResponse response(Organisation o) {
        return new OrganisationResponse(o.id, o.name, o.serviceType, o.governorate, o.contactPhone, o.address, o.cost,
                o.languages.stream().sorted().toList(), o.verifiedDate, o.verificationMethod, o.fictional, verification.due(o.verifiedDate));
    }

    private static void validateFilter(String value, Set<String> known) {
        if (!value.isEmpty() && !known.contains(value)) badRequest();
    }

    private static void badRequest() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid directory values"); }
}
