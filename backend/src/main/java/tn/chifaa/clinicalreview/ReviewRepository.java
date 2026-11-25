package tn.chifaa.clinicalreview;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Repository
class ReviewRepository {
    private final JdbcClient db;
    ReviewRepository(JdbcClient db) { this.db = db; }

    // UNION termine également sur un ancien chaînage invalide. Deux versions d'une même
    // famille verrouillent la même racine avant de relire leur état courant.
    private static final String ANCESTORS = """
            with recursive ancestors(id,prev) as (
                select content_id,previous_version_id from kb_items where content_id=:id
                union select k.content_id,k.previous_version_id from kb_items k join ancestors a on k.content_id=a.prev
            )
            """;
    private static final String LINE = ANCESTORS + """
            , versions(id,depth) as (
                select id,0 from ancestors where prev is null
                union all select k.content_id,v.depth+1 from kb_items k join versions v on k.previous_version_id=v.id
            )
            """;
    private static final String SUMMARY = "k.content_id,k.title,k.language,k.content_version,k.approval_status,k.previous_version_id,k.is_fictional as fictional";

    String staffRole(String subject, boolean lock) {
        return db.sql("select role from staff where staff_id=:sub and active" + (lock ? " for share" : ""))
                .param("sub",subject).query(String.class).optional().orElse("");
    }

    void lockFamily(String id) {
        var root = db.sql(ANCESTORS + """
                select k.content_id from kb_items k join ancestors a on k.content_id=a.id where a.prev is null for update of k
                """).param("id",id).query(String.class).optional();
        if (root.isEmpty()) {
            get(id); // 404 si absente, 409 si une ancienne chaîne n'a pas de racine.
            throw new ResponseStatusException(CONFLICT);
        }
    }

    ReviewItem get(String id) {
        return db.sql("select * from kb_items where content_id=:id").param("id",id)
                .query(ReviewRepository::item).optional().orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    private static ReviewItem item(ResultSet r,int index) throws SQLException {
        var content = new EditorialContent(r.getString("title"),r.getString("topic"),r.getString("language"),
                r.getString("content_text"),r.getString("source_type"),r.getString("source_reference"),
                r.getString("evidence_level"),r.getString("local_applicability"),r.getObject("expiry_date",LocalDate.class));
        return new ReviewItem(r.getString("content_id"),r.getString("content_version"),r.getString("previous_version_id"),
                r.getString("superseded_by"),r.getString("approval_status"),r.getString("clinical_reviewer"),
                r.getObject("review_date",LocalDate.class),r.getBoolean("is_fictional"),content);
    }

    List<ReviewItem.Summary> list(String status,int offset,int limit) {
        var query = db.sql("select " + SUMMARY + " from kb_items k " + (status == null ? "" : "where approval_status=:status ")
                + "order by content_id limit :limit offset :offset").param("limit",limit).param("offset",offset);
        if (status != null) query = query.param("status",status);
        return query.query(ReviewItem.Summary.class).list();
    }

    List<ReviewItem.Summary> history(String id,int offset,int limit) {
        return db.sql(LINE + "select " + SUMMARY + " from versions v join kb_items k on k.content_id=v.id "
                + "order by v.depth limit :limit offset :offset").param("id",id).param("limit",limit).param("offset",offset)
                .query(ReviewItem.Summary.class).list();
    }

    ReviewItem tail(String id) {
        return db.sql(LINE + "select k.* from versions v join kb_items k on k.content_id=v.id order by depth desc limit 1")
                .param("id",id).query(ReviewRepository::item).single();
    }

    ReviewItem insert(EditorialContent content,String version,String previous,boolean fictional) {
        // Espace KB-#### partagé avec les imports M4. Réserver un code libre dans la transaction :
        // une création rejetée ne consomme pas un des 9 999 identifiants générés.
        db.sql("select pg_advisory_xact_lock(21102,1)").query((r,n) -> true).single();
        String id = db.sql("""
                select 'KB-' || lpad(n::text,4,'0') from generate_series(1,9999) n
                where not exists(select from kb_items where content_id='KB-' || lpad(n::text,4,'0'))
                order by n limit 1
                """).query(String.class).optional().orElseThrow(() -> new ResponseStatusException(CONFLICT));
        return db.sql("""
                insert into kb_items(content_id,previous_version_id,content_version,title,topic,language,content_text,
                    source_type,source_reference,evidence_level,local_applicability,expiry_date,is_fictional)
                values (:id,:previous,:version,:title,:topic,:language,:contentText,:sourceType,:sourceReference,
                    :evidenceLevel,:localApplicability,:expiryDate,:fictional) returning *
                """).param("title",content.title()).param("topic",content.topic()).param("language",content.language())
                .param("contentText",content.contentText()).param("sourceType",content.sourceType())
                .param("sourceReference",content.sourceReference()).param("evidenceLevel",content.evidenceLevel())
                .param("localApplicability",content.localApplicability()).param("expiryDate",content.expiryDate())
                .param("id",id).param("previous",previous).param("version",version)
                .param("fictional",fictional).query(ReviewRepository::item).single();
    }

    ReviewItem retire(String id,String successor) {
        return db.sql("update kb_items set approval_status='Retired',superseded_by=:next where content_id=:id returning *")
                .param("id",id).param("next",successor).query(ReviewRepository::item).single();
    }

    ReviewItem approve(String id,String staff,LocalDate expiry) {
        // Le lien de publication suit la même chaîne que l'historique, même si plusieurs brouillons ont été relus.
        db.sql(ANCESTORS + """
                update kb_items old set approval_status='Retired',superseded_by=a.id
                from ancestors a where old.content_id=a.prev and old.approval_status='Clinically approved'
                """).param("id",id).update();
        return db.sql("""
                update kb_items set approval_status='Clinically approved',clinical_reviewer=:staff,
                    review_date=(current_timestamp at time zone 'UTC')::date,expiry_date=:expiry
                where content_id=:id returning *
                """).param("staff",staff).param("expiry",expiry).param("id",id).query(ReviewRepository::item).single();
    }
}
