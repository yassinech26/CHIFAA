package tn.chifaa.safety;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;

@Service
class SafetyService {
    private final SafetyRepository events;
    SafetyService(SafetyRepository events) { this.events = events; }

    private void authorize(String staff, boolean lock) {
        if (!events.reviewer(staff, lock)) throw new ResponseStatusException(FORBIDDEN);
    }

    @Transactional(readOnly = true)
    List<SafetyEvent> list(String staff, String status, int offset, int limit) {
        authorize(staff, false);
        if (status != null && !SafetyPatch.STATUSES.contains(status)) throw new ResponseStatusException(BAD_REQUEST);
        return events.list(status, offset, limit);
    }

    @Transactional
    SafetyEvent patch(String staff, UUID id, JsonNode body) {
        authorize(staff, true);
        SafetyPatch patch = new SafetyPatch(body);
        SafetyEvent event = events.lock(id);
        String status = patch.value("status", event.status());
        String reviewer = patch.value("reviewerId", event.reviewerId());
        String action = patch.value("actionTaken", event.actionTaken());
        String outcome = patch.value("referralOutcome", event.referralOutcome());
        String org = patch.value("orgId", event.orgId());

        int steps = SafetyPatch.STATUSES.indexOf(status) - SafetyPatch.STATUSES.indexOf(event.status());
        if (steps < 0 || steps > 1) throw new ResponseStatusException(CONFLICT);
        boolean starting = event.status().equals("Open") && status.equals("In progress");
        if (starting && reviewer == null && !body.has("reviewerId")) reviewer = staff;

        boolean sameReviewer = Objects.equals(reviewer, event.reviewerId());
        if (steps == 0 && sameReviewer && Objects.equals(action, event.actionTaken())
                && Objects.equals(outcome, event.referralOutcome()) && Objects.equals(org, event.orgId())) return event;
        if (event.status().equals("Closed")) throw new ResponseStatusException(CONFLICT);
        if (reviewer == null && !status.equals("Open")) throw new ResponseStatusException(BAD_REQUEST);
        if (reviewer != null && (!sameReviewer || starting) && !events.reviewer(reviewer, true))
            throw new ResponseStatusException(BAD_REQUEST);
        return events.update(id, status, reviewer, action, outcome, org);
    }
}
