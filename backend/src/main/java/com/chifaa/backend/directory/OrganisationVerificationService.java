package com.chifaa.backend.directory;
import java.time.*;
import org.springframework.stereotype.Service;

@Service
public class OrganisationVerificationService {
    LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }
    LocalDate cutoff() { return today().minusMonths(12); }
    boolean due(LocalDate date) { return date == null || date.isBefore(cutoff()); }
    void verify(Organisation organisation, String method) {
        organisation.verifiedDate = today();
        organisation.verificationMethod = method.strip();
    }
}
