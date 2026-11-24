package tn.chifaa.users;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/users/me")
class ProfileController {
    private final ProfileService service;
    ProfileController(ProfileService service) { this.service=service; }
    @GetMapping Profile me(@AuthenticationPrincipal UUID pid) { return service.get(pid); }
    @PutMapping Profile edit(@AuthenticationPrincipal UUID pid,@Valid @RequestBody Profile body) { return service.edit(pid,body); }
}
