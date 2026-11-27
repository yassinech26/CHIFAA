package tn.chifaa.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import tn.chifaa.users.Profile;

public final class AuthDtos {
    private AuthDtos() {}
    public record Register(@NotNull @Pattern(regexp="\\+[1-9][0-9]{7,14}") String phone,
        @NotNull @AssertTrue Boolean adult, @NotNull @AssertTrue Boolean service,
        @NotNull Boolean health, @NotNull Boolean survey,
        @NotNull @Pattern(regexp="v1\\.0") String version,
        @NotNull @Valid Profile profile) {}
    public record Login(@NotNull @Pattern(regexp="\\+[1-9][0-9]{7,14}") String phone) {}
    public record Verify(@NotNull UUID challengeId, @NotNull @Pattern(regexp="[0-9]{6}") String code) {}
    public record Challenge(UUID challengeId, Instant expiresAt) {}
    public record Session(String token, Instant expiresAt, UUID userPid) {}
}
