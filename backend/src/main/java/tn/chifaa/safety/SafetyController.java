package tn.chifaa.safety;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/safety-events")
class SafetyController {
    private final SafetyService service;
    SafetyController(SafetyService service) { this.service = service; }

    @GetMapping
    List<SafetyEvent> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) String status,
                           @RequestParam(defaultValue="0") @Min(0) int offset,
                           @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return service.list(jwt.getSubject(), status, offset, limit);
    }

    @PatchMapping("/{id}")
    SafetyEvent update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody JsonNode body) {
        return service.patch(jwt.getSubject(), id, body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail invalidReference() { return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST); }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable() { return ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE); }
}
