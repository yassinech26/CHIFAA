package com.chifaa.backend.safety;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("chifaa.safety")
@Validated
public record SafetyProperties(List<@Valid Rule> rules, List<@Valid Resource> resources) {
    public SafetyProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    public record Rule(@NotBlank String phrase, @NotBlank @Size(max=60) String type,
        @NotBlank @Pattern(regexp="Low|Medium|High|Critical") String severity) {}

    public record Resource(@NotBlank @Pattern(regexp="French|Darija|MSA|Mixed") String language,
        @NotBlank @Size(max=160) String name, @NotNull URI url) {
        @AssertTrue
        public boolean isSecureUrl() {
            return url != null && "https".equalsIgnoreCase(url.getScheme())
                    && url.getHost() != null && url.getUserInfo() == null;
        }
    }
}
