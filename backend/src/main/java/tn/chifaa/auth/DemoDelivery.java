package tn.chifaa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Component
class DemoDelivery implements OtpDelivery {
    private final boolean enabled;
    DemoDelivery(@Value("${chifaa.demo-otp}") boolean enabled) { this.enabled = enabled; }
    public void send(UUID challenge, String code) {
        if (!enabled || System.console() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interactive demo console required");
        System.console().printf("SMS fictif %s : %s%n", challenge, code);
    }
}
