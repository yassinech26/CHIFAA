package com.chifaa.backend.safety;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/safety-events")
public class SafetyEventController {
    private final SafetyEventService service;
    public SafetyEventController(SafetyEventService service) { this.service = service; }

    @GetMapping
    List<SafetyEvent> list(@RequestParam(defaultValue="") String severity, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="0") int page) { return service.list(severity,status,page); }

    @GetMapping("/reviewers")
    List<SafetyEventService.Reviewer> reviewers() { return service.reviewers(); }

    @GetMapping("/statistics")
    SafetyEventService.Statistics statistics() { return service.statistics(); }

    @GetMapping("/{id}")
    SafetyEvent get(@PathVariable UUID id) { return service.get(id); }

    @PatchMapping("/{id}")
    SafetyEvent patch(@PathVariable UUID id, @Valid @RequestBody SafetyEventService.Update changes) {
        return service.update(id,changes);
    }
}
