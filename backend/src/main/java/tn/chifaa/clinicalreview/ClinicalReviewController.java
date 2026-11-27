package tn.chifaa.clinicalreview;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/kb")
class ClinicalReviewController {
    private static final String ID = "KB-[0-9]{4}";
    private final ClinicalReviewService service;
    ClinicalReviewController(ClinicalReviewService service) { this.service = service; }

    @GetMapping
    List<ReviewItem.Summary> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") @Min(0) int offset,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return service.list(jwt.getSubject(),status,offset,limit);
    }

    @GetMapping("/{id}")
    ReviewItem get(@AuthenticationPrincipal Jwt jwt,@PathVariable @Pattern(regexp=ID) String id) {
        return service.get(jwt.getSubject(),id);
    }

    @GetMapping("/{id}/versions")
    List<ReviewItem.Summary> history(@AuthenticationPrincipal Jwt jwt,@PathVariable @Pattern(regexp=ID) String id,
            @RequestParam(defaultValue="0") @Min(0) int offset,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return service.history(jwt.getSubject(),id,offset,limit);
    }

    @PostMapping
    ResponseEntity<ReviewItem> create(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode body) {
        ReviewItem draft = service.create(jwt.getSubject(),body);
        return ResponseEntity.created(location(draft)).body(draft);
    }

    @PatchMapping("/{id}")
    ResponseEntity<ReviewItem> patch(@AuthenticationPrincipal Jwt jwt,@PathVariable @Pattern(regexp=ID) String id,
                                     @RequestBody JsonNode body) {
        ReviewItem item = service.patch(jwt.getSubject(),id,body);
        return ResponseEntity.ok().location(location(item)).body(item);
    }

    private static URI location(ReviewItem item) { return URI.create("/api/admin/kb/" + item.contentId()); }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail conflict() { return ProblemDetail.forStatus(HttpStatus.CONFLICT); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail invalid() { return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST); }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail unavailable() { return ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE); }
}
