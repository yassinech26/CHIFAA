package tn.chifaa.knowledge;

import java.time.LocalDate;

public record ItemDetail(String contentId, String title, String topic, String language, String contentText,
                         String sourceType, String sourceReference, String evidenceLevel, String localApplicability,
                         String contentVersion, LocalDate reviewDate, LocalDate expiryDate, boolean fictional,
                         String notice) {}
