package tn.chifaa.knowledge;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeMigrationTest {
    private static EmbeddedPostgres pg;
    private Connection db;

    @BeforeAll
    static void migrate() throws Exception {
        pg = EmbeddedPostgres.start();
        Flyway flyway = Flyway.configure()
                .dataSource(pg.getPostgresDatabase())
                .locations("classpath:fixture", "classpath:db/migration")
                .placeholders(Map.of("at044Source1", "Test A", "at044Source2", "Test B",
                        "at044Source3", "Test C", "at044Source4", "Test D", "at044Source5", "Test E"))
                .load();
        flyway.migrate();
        flyway.validate();
    }

    @AfterAll
    static void stop() throws Exception {
        if (pg != null) pg.close();
    }

    @BeforeEach
    void begin() throws SQLException {
        db = pg.getPostgresDatabase().getConnection();
        db.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws SQLException {
        try (Connection connection = db) {
            connection.rollback();
        }
    }

    private void draft(String id) throws SQLException {
        try (PreparedStatement insert = db.prepareStatement("""
                INSERT INTO kb_items (content_id, title, topic, content_text, language,
                                      source_type, source_reference, content_version)
                VALUES (?, 'Titre de test', 'Test', 'Texte fictif', 'French', 'Test A', 'Test', 'v1.0')
                """)) {
            insert.setString(1, id);
            insert.executeUpdate();
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }

    private String scalar(String sql) throws SQLException {
        try (Statement statement = db.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private void approve() throws SQLException {
        execute("""
                UPDATE kb_items SET approval_status = 'Clinically approved', clinical_reviewer = 'TEST-01',
                                    review_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 10
                WHERE content_id = 'KB-0001'
                """);
    }

    @Test
    void showsOnlyApprovedFictionWithItsNoticeAndHidesRetiredItems() throws SQLException {
        draft("KB-0001");
        assertEquals("0", scalar("SELECT count(*) FROM kb_visible_items"));
        approve();
        assertEquals("Contenu fictif – non validé", scalar("SELECT notice FROM kb_visible_items"));
        execute("UPDATE kb_items SET approval_status = 'Retired'");
        assertEquals("0", scalar("SELECT count(*) FROM kb_visible_items"));
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,0", "1,1"})
    void expiryUsesUtcDayEvenInAnotherDatabaseTimezone(int offset, String expected) throws SQLException {
        draft("KB-0001");
        approve();
        execute("SET LOCAL TIME ZONE 'Pacific/Kiritimati'");
        execute("UPDATE kb_items SET expiry_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date + " + offset);
        assertEquals(expected, scalar("SELECT count(*) FROM kb_visible_items"));
    }

    @Test
    void replacingAnItemKeepsOnlyItsSuccessorVisible() throws SQLException {
        draft("KB-0001");
        approve();
        draft("KB-0002");
        execute("""
                UPDATE kb_items SET content_version = 'v2.0', clinical_reviewer = 'TEST-01',
                    approval_status = 'Clinically approved', review_date = CURRENT_DATE - 1
                WHERE content_id = 'KB-0002'
                """);
        execute("UPDATE kb_items SET superseded_by = 'KB-0002', approval_status = 'Retired' WHERE content_id = 'KB-0001'");
        assertEquals("KB-0002", scalar("SELECT string_agg(content_id, ',') FROM kb_visible_items"));
        execute("DELETE FROM kb_items WHERE content_id = 'KB-0002'");
        assertNull(scalar("SELECT superseded_by FROM kb_items WHERE content_id = 'KB-0001'"));
        assertEquals("0", scalar("SELECT count(*) FROM kb_visible_items"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "approval_status = 'Clinically approved', review_date = CURRENT_DATE | 23514",
        "approval_status = 'Clinically approved', clinical_reviewer = 'TEST-01' | 23514",
        "clinical_reviewer = 'UNKNOWN' | 23503",
        "source_type = 'Hors catalogue' | 23514",
        "review_date = CURRENT_DATE, expiry_date = CURRENT_DATE | 23514",
        "superseded_by = 'KB-0001' | 23514"
    })
    void databaseRejectsInvalidMetadata(String assignment, String sqlState) throws SQLException {
        draft("KB-0001");
        SQLException error = assertThrows(SQLException.class, () -> execute("UPDATE kb_items SET " + assignment));
        assertEquals(sqlState, error.getSQLState());
    }

    @Test
    void reviewerOfAnApprovedItemCannotBeDeleted() throws SQLException {
        draft("KB-0001");
        approve();
        SQLException error = assertThrows(SQLException.class, () -> execute("DELETE FROM staff"));
        assertEquals("23503", error.getSQLState());
    }
}
