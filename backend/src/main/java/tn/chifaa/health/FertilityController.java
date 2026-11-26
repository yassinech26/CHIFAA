package tn.chifaa.health;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/health/fertility")
class FertilityController {
    record Item(String title,String contentText,String approvalStatus,LocalDate expiryDate) {}
    private final HealthService health;
    private final String url;
    FertilityController(HealthService health,@Value("${chifaa.library-url:}") String url) { this.health=health; this.url=url; }
    @GetMapping List<Item> items(@AuthenticationPrincipal UUID pid,@RequestParam(defaultValue="French") String language) {
        if (!Boolean.TRUE.equals(health.profile(pid).fertilityInfoInterest())) return List.of();
        if (url.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (!Set.of("French","MSA","Darija","Mixed").contains(language)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try {
            var result=RestClient.create(url).get().uri(u->u.queryParam("topic","Fertility").queryParam("language",language).build())
                .retrieve().body(new ParameterizedTypeReference<List<Item>>() {});
            // Recheck following the external read in case permission changed during the request.
            if (!Boolean.TRUE.equals(health.profile(pid).fertilityInfoInterest())) return List.of();
            if (result==null) return List.of();
            return result.stream().filter(i->"Clinically approved".equals(i.approvalStatus())
                && i.expiryDate()!=null && !i.expiryDate().isBefore(LocalDate.now(ZoneOffset.UTC))).toList();
        } catch (RestClientException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); }
    }
}
