package tn.chifaa.users;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record Profile(
    @NotNull @Pattern(regexp="18–29|30–39|40–49|50–59|60\\+") String ageBand,
    @NotNull @Pattern(regexp="TN-(11|12|13|14|21|22|23|31|32|33|34|41|42|43|51|52|53|61|71|72|73|81|82|83)") String governorate,
    @NotNull @Pattern(regexp="Darija|French|MSA|Mixed") String preferredLanguage
) {
    public void apply(Patient patient) {
        patient.ageBand = ageBand;
        patient.governorate = governorate;
        patient.preferredLanguage = preferredLanguage;
    }
    public static Profile from(Patient patient) {
        return new Profile(patient.ageBand, patient.governorate, patient.preferredLanguage);
    }
}
