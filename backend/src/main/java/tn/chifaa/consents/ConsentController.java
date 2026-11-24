package tn.chifaa.consents;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/consents")
public class ConsentController {
    public record Change(@NotNull @Pattern(regexp="Service use|Health data|Survey") String purpose,
        @NotNull @Pattern(regexp="v1\\.0") String version,@NotNull Boolean given) {}
    private final ConsentService service;
    ConsentController(ConsentService service) { this.service=service; }
    @GetMapping List<ConsentService.Evidence> list(@AuthenticationPrincipal UUID pid) { return service.list(pid); }
    @PutMapping void change(@AuthenticationPrincipal UUID pid,@Valid @RequestBody Change body) { service.change(pid,body); }
}
