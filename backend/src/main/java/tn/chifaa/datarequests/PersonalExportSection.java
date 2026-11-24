package tn.chifaa.datarequests;

import java.util.UUID;

/** Implement in a feature module to include the authenticated patient's records in both export formats. */
public interface PersonalExportSection {
    String name();
    Object read(UUID patient);
    default boolean requiresHealthConsent() { return false; }
}
