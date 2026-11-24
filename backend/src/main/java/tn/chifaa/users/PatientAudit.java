package tn.chifaa.users;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.LoggerFactory;

/** Patient access events; staff access_log entries are produced by the separate staff workflow. */
@Component
public class PatientAudit {
    public record Access(UUID actor,String resource,String action,String purpose,Instant at) {}
    private final ApplicationEventPublisher events;
    public PatientAudit(ApplicationEventPublisher events) { this.events=events; }
    public void record(UUID actor,String resource,String action,String purpose) {
        events.publishEvent(new Access(actor,resource,action,purpose,Instant.now()));
    }
    @TransactionalEventListener
    public void log(Access e) {
        LoggerFactory.getLogger("chifaa.patient.audit").info("actor={} resource={} action={} purpose={} at={}",
            e.actor(),e.resource(),e.action(),e.purpose(),e.at());
    }
}
