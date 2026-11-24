package tn.chifaa.health;

import java.util.*;
import java.time.LocalDate;
import jakarta.validation.constraints.*;

public final class HealthDtos {
    private HealthDtos() {}
    public record Form(
        @Pattern(regexp="Breast|Cervical|Other|Prefer not to say") String cancerType,
        @Size(max=30) String carePhase,
        @Pattern(regexp="<1 yr|1–2 yrs|3–5 yrs|>5 yrs") String diagnosisPeriodBand,
        @Min(1950) Integer diagnosisYear,
        @Pattern(regexp="Public hospital|Private clinic|Abroad|Prefer not to say") String facilityType,
        Boolean fertilityInfoInterest,
        @NotNull @Size(max=8) List<@NotNull @Pattern(regexp="Surgery|Reconstruction|Chemotherapy|Radiotherapy|Hormone therapy|Targeted therapy|Immunotherapy|Prefer not to say") String> treatments) {}
    public record Catalog(List<String> carePhases,List<String> sideEffects) {}
    public record CheckinInput(@NotNull @PastOrPresent LocalDate date,@NotNull @Size(max=11) List<@NotBlank @Size(max=25) String> effects) {}
    public record Checkin(UUID id,LocalDate date,List<String> effects) {}
}
