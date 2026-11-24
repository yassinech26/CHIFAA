package tn.chifaa.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final OtpService otp;
    private final Accounts accounts;
    private final Sessions sessions;
    AuthController(OtpService otp, Accounts accounts, Sessions sessions) {
        this.otp = otp; this.accounts = accounts; this.sessions = sessions;
    }
    @PostMapping("/register") AuthDtos.Challenge register(@Valid @RequestBody AuthDtos.Register body) {
        return otp.register(body);
    }
    @PostMapping("/login") AuthDtos.Challenge login(@Valid @RequestBody AuthDtos.Login body) {
        return otp.login(body.phone());
    }
    @PostMapping("/verify") AuthDtos.Session verify(@Valid @RequestBody AuthDtos.Verify body) {
        return sessions.open(accounts.authenticate(otp.consume(body)));
    }
    @PostMapping("/logout") void logout(@RequestHeader("Authorization") String authorization) {
        sessions.close(authorization.substring(7));
    }
}
