package com.chifaa.backend.directory;
import java.time.LocalDate;
import java.util.List;

public record OrganisationResponse(String id, String name, String serviceType, String governorate, String contactPhone,
    String address, String cost, List<String> languages, LocalDate verifiedDate, String verificationMethod,
    boolean fictional, boolean verificationDue) {}
