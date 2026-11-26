package tn.chifaa.health;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import static tn.chifaa.health.HealthDtos.*;
@RestController
@RequestMapping("/api/health")
class HealthController {
    private final HealthService service;
    HealthController(HealthService service) { this.service=service; }
    @GetMapping("/catalog") Catalog catalog(@AuthenticationPrincipal UUID pid) { return service.catalog(pid); }
    @GetMapping("/profile") Form profile(@AuthenticationPrincipal UUID pid) { return service.profile(pid); }
    @PutMapping("/profile") Form save(@AuthenticationPrincipal UUID pid,@Valid @RequestBody Form f) { return service.save(pid,f); }
    @PostMapping("/checkins") Checkin add(@AuthenticationPrincipal UUID pid,@Valid @RequestBody CheckinInput f) { return service.add(pid,f); }
    @GetMapping("/checkins") List<Checkin> list(@AuthenticationPrincipal UUID pid) { return service.history(pid,null); }
    @GetMapping("/checkins/{id}") Checkin one(@AuthenticationPrincipal UUID pid,@PathVariable UUID id) {
        return service.history(pid,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
