package com.chifaa.backend.directory;

import jakarta.validation.constraints.*;
import java.util.List;

public record OrganisationRequest(
    @NotBlank @Size(max=180) String name,
    @NotBlank @Size(max=40) String serviceType,
    @NotBlank String governorate,
    @Pattern(regexp="^$|[+0-9() -]{5,32}") String contactPhone,
    @Size(max=300) String address,
    @NotBlank @Pattern(regexp="FREE|COVERED_BY_CNAM|PAID|UNKNOWN") String cost,
    @NotNull @Size(max=3) List<@NotBlank String> languages
) {}
