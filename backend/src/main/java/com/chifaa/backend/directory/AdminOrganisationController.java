package com.chifaa.backend.directory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/organisations")
public class AdminOrganisationController {
    record Verification(@NotBlank @Size(max=120) String method) {}
    private final OrganisationService service;
    public AdminOrganisationController(OrganisationService service) { this.service = service; }

    @GetMapping
    OrganisationService.Results list(@RequestParam(defaultValue="false") boolean stale,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        return service.search("", "", "", stale, page, size);
    }

    @GetMapping("/statistics")
    java.util.Map<String, Long> statistics() { return service.statistics(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrganisationResponse create(@Valid @RequestBody OrganisationRequest request) { return service.save(null, request); }

    @PatchMapping("/{id}")
    OrganisationResponse update(@PathVariable String id, @Valid @RequestBody OrganisationRequest request) { return service.save(id, request); }

    @PostMapping("/{id}/verify")
    OrganisationResponse verify(@PathVariable String id, @Valid @RequestBody Verification request) {
        return service.verify(id, request.method());
    }
}
