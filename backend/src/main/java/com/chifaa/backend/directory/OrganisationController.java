package com.chifaa.backend.directory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organisations")
public class OrganisationController {
    private final OrganisationService service;
    public OrganisationController(OrganisationService service) { this.service = service; }

    @GetMapping
    OrganisationService.Results list(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="") String governorate,
            @RequestParam(defaultValue="") String category, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) {
        return service.search(q, governorate, category, false, page, size);
    }

    @GetMapping("/{id}")
    OrganisationResponse get(@PathVariable String id) { return service.get(id); }
}
