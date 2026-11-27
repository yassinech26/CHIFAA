package tn.chifaa.clinicalreview;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
class ClinicalReviewService {
    private static final String CONTENT = "Content team", CLINICAL = "Clinical reviewer";
    private static final String DRAFT = "Draft", APPROVED = "Clinically approved", RETIRED = "Retired";
    private final ReviewRepository items;

    ClinicalReviewService(ReviewRepository items) { this.items = items; }

    private String role(String subject,boolean writing) {
        String role = subject == null ? "" : items.staffRole(subject,writing);
        if (!CONTENT.equals(role) && !CLINICAL.equals(role)) throw new ResponseStatusException(FORBIDDEN);
        return role;
    }

    private void require(String actual,String required) {
        if (!required.equals(actual)) throw new ResponseStatusException(FORBIDDEN);
    }

    @Transactional(readOnly=true)
    List<ReviewItem.Summary> list(String sub,String status,int offset,int limit) {
        role(sub,false);
        if (status != null && !Set.of(DRAFT,APPROVED,RETIRED).contains(status)) throw EditorialContent.invalid();
        return items.list(status,offset,limit);
    }

    @Transactional(readOnly=true)
    ReviewItem get(String sub,String id) {
        role(sub,false);
        return items.get(id);
    }

    @Transactional(readOnly=true)
    List<ReviewItem.Summary> history(String sub,String id,int offset,int limit) {
        role(sub,false);
        items.get(id);
        return items.history(id,offset,limit);
    }

    ReviewItem create(String sub,JsonNode body) {
        require(role(sub,true),CONTENT);
        return items.insert(EditorialContent.from(body,null),"v1.0",null,true);
    }

    ReviewItem patch(String sub,String id,JsonNode body) {
        boolean decision = body.has("approvalStatus");
        require(role(sub,true),decision ? CLINICAL : CONTENT);
        items.lockFamily(id);
        ReviewItem target = items.get(id);
        if (!decision) return revise(target,body);
        String status = body.path("approvalStatus").asText();
        if (APPROVED.equals(status)) {
            EditorialContent.checkFields(body,Set.of("approvalStatus","expiryDate"));
            LocalDate expiry = EditorialContent.expiry(body,target.content().expiryDate());
            if (APPROVED.equals(target.approvalStatus()) && Objects.equals(target.content().expiryDate(),expiry)) return target;
            if (!DRAFT.equals(target.approvalStatus())) throw new ResponseStatusException(CONFLICT);
            return items.approve(id,sub,expiry);
        }
        if (!RETIRED.equals(status)) throw EditorialContent.invalid();
        EditorialContent.checkFields(body,Set.of("approvalStatus"));
        if (RETIRED.equals(target.approvalStatus())) return target;
        if (APPROVED.equals(target.approvalStatus())) {
            ReviewItem latest = items.tail(id);
            if (DRAFT.equals(latest.approvalStatus())) items.retire(latest.contentId(),null);
        }
        return items.retire(id,null);
    }

    private ReviewItem revise(ReviewItem target,JsonNode body) {
        if (RETIRED.equals(target.approvalStatus())) throw new ResponseStatusException(CONFLICT);
        EditorialContent next = EditorialContent.from(body,target.content());
        if (next.equals(target.content())) return target;
        ReviewItem tail = items.tail(target.contentId());
        if (DRAFT.equals(tail.approvalStatus()) && !tail.contentId().equals(target.contentId()))
            throw new ResponseStatusException(CONFLICT);
        String current = tail.contentVersion();
        int dot = current.indexOf('.');
        String version = current.substring(0,dot+1) + (Integer.parseInt(current.substring(dot+1)) + 1);
        if (version.length() > 10) throw new ResponseStatusException(CONFLICT);
        ReviewItem draft = items.insert(next,version,tail.contentId(),target.fictional());
        if (!APPROVED.equals(tail.approvalStatus())) items.retire(tail.contentId(),draft.contentId());
        return draft;
    }
}
