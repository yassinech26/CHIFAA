package tn.chifaa.safety;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Le PATCH distingue une propriété absente d'une valeur explicitement effacée. */
record SafetyPatch(JsonNode fields) {
    static final List<String> STATUSES = List.of("Open", "In progress", "Resolved", "Closed");
    private static final Set<String> KEYS = Set.of("status", "reviewerId", "actionTaken", "referralOutcome", "orgId");
    private static final Set<String> ACTIONS = Set.of("Resource shared", "Human contacted", "Referral", "None needed");
    private static final Set<String> OUTCOMES = Set.of("Contacted", "Attended", "Declined", "Unknown", "N/A");

    SafetyPatch {
        if (fields == null || !fields.isObject() || fields.isEmpty()) throw invalid();
        fields.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!KEYS.contains(key)) throw invalid();
            if (value.isNull() && !key.equals("status")) return;
            if (!value.isTextual()) throw invalid();
            String text = value.textValue();
            boolean valid = switch (key) {
                case "status" -> STATUSES.contains(text);
                case "actionTaken" -> ACTIONS.contains(text);
                case "referralOutcome" -> OUTCOMES.contains(text);
                case "reviewerId" -> validId(text, 10);
                case "orgId" -> validId(text, 7);
                default -> false;
            };
            if (!valid) throw invalid();
        });
    }

    private static boolean validId(String text, int max) {
        return !text.isBlank() && text.length() <= max && text.indexOf(0) < 0;
    }

    private static ResponseStatusException invalid() { return new ResponseStatusException(BAD_REQUEST); }

    String value(String field, String previous) {
        return fields.has(field) ? fields.get(field).textValue() : previous;
    }
}
