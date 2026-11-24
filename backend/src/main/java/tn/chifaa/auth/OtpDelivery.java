package tn.chifaa.auth;

import java.util.UUID;

public interface OtpDelivery {
    void send(UUID challenge, String code);
}
