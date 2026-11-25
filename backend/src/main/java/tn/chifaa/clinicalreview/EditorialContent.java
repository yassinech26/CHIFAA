package tn.chifaa.clinicalreview;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Champs éditables. Les métadonnées de validation sont attribuées exclusivement par le serveur. */
record EditorialContent(String title, String topic, String language, String contentText, String sourceType,
                        String sourceReference, String evidenceLevel, String localApplicability, LocalDate expiryDate) {
    private static final Set<String> FIELDS = Set.of("title","topic","language","contentText","sourceType",
            "sourceReference","evidenceLevel","localApplicability","expiryDate");

    static EditorialContent from(JsonNode body, EditorialContent base) {
        checkFields(body, FIELDS);
        return new EditorialContent(
                text(body,"title",base == null ? null : base.title,200,true),
                text(body,"topic",base == null ? null : base.topic,50,true),
                text(body,"language",base == null ? null : base.language,10,true),
                text(body,"contentText",base == null ? null : base.contentText,Integer.MAX_VALUE,true),
                text(body,"sourceType",base == null ? null : base.sourceType,25,true),
                text(body,"sourceReference",base == null ? null : base.sourceReference,Integer.MAX_VALUE,true),
                text(body,"evidenceLevel",base == null ? null : base.evidenceLevel,20,false),
                text(body,"localApplicability",base == null ? null : base.localApplicability,20,false),
                expiry(body,base == null ? null : base.expiryDate));
    }

    static void checkFields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject() || body.isEmpty()) throw invalid();
        body.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw invalid(); });
    }

    private static String text(JsonNode body, String key, String fallback, int max, boolean required) {
        JsonNode node = body.get(key);
        if (node != null && !node.isNull() && !node.isTextual()) throw invalid();
        String value = node == null ? fallback : node.textValue();
        if (value == null) {
            if (required) throw invalid();
        } else if (value.isBlank() || value.indexOf(0) >= 0 || value.codePointCount(0,value.length()) > max) throw invalid();
        return value;
    }

    static LocalDate expiry(JsonNode body, LocalDate previous) {
        if (!body.has("expiryDate")) return previous;
        String value = text(body,"expiryDate",null,10,false);
        try { return value == null ? null : LocalDate.parse(value); }
        catch (DateTimeParseException ex) { throw invalid(); }
    }

    static ResponseStatusException invalid() { return new ResponseStatusException(BAD_REQUEST); }
}
