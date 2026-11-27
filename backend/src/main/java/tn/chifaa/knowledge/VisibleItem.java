package tn.chifaa.knowledge;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

@Entity
@Immutable
@Table(name = "kb_visible_items")
class VisibleItem {
    @Id
    private String contentId;
    private String title;
    private String topic;
    private String language;
    private String contentText;
    private String sourceType;
    private String sourceReference;
    private String evidenceLevel;
    private String localApplicability;
    private String contentVersion;
    private LocalDate reviewDate;
    private LocalDate expiryDate;
    @jakarta.persistence.Column(name = "is_fictional")
    private boolean fictional;
    private String notice;
}
