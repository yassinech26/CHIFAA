package tn.chifaa.knowledge;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/kb")
class KnowledgeController {
    private final KnowledgeService service;

    KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping
    ItemPage list(@AuthenticationPrincipal UUID user, @RequestParam(defaultValue = "") @Size(max = 200) String q,
                  @RequestParam(defaultValue = "") @Size(max = 50) String topic,
                  @RequestParam(defaultValue = "") @Pattern(regexp = "(?:French|MSA|Darija)?") String language,
                  @RequestParam(defaultValue = "") @Pattern(regexp = "(?:KB-[0-9]{4})?") String after,
                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.search(user, q, topic, language, after, limit);
    }

    @GetMapping("/{id}")
    ItemDetail detail(@AuthenticationPrincipal UUID user, @PathVariable @Pattern(regexp = "KB-[0-9]{4}") String id) {
        return service.find(user, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
