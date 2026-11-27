package tn.chifaa.clinicalreview;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;

record ReviewItem(String contentId, String contentVersion, String previousVersionId, String supersededBy,
                  String approvalStatus, String clinicalReviewer, LocalDate reviewDate, boolean fictional,
                  @JsonUnwrapped EditorialContent content) {

    public String getNotice() { return notice(fictional); }

    private static String notice(boolean fictional) { return fictional ? "Contenu fictif – non validé" : null; }

    record Summary(String contentId, String title, String language, String contentVersion,
                   String approvalStatus, String previousVersionId, boolean fictional) {
        public String getNotice() { return notice(fictional); }
    }
}
