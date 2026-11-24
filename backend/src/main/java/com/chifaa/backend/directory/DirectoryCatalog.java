package com.chifaa.backend.directory;
import java.util.Set;

final class DirectoryCatalog {
    static final Set<String> SERVICE_TYPES = Set.of("ONCOLOGY","PSYCHOLOGY","LEGAL","FERTILITY","FINANCIAL",
        "PEER_SUPPORT","PROSTHESES_WIGS","TRANSPORT","CNAM");
    static final Set<String> GOVERNORATES = Set.of("TN-11","TN-12","TN-13","TN-14","TN-21","TN-22","TN-23",
        "TN-31","TN-32","TN-33","TN-34","TN-41","TN-42","TN-43","TN-51","TN-52","TN-53","TN-61",
        "TN-71","TN-72","TN-73","TN-81","TN-82","TN-83");
    static final Set<String> LANGUAGES = Set.of("French","Darija","MSA");

    private DirectoryCatalog() {}
}
