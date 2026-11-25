"""Reconstitue deux copies PostgreSQL jetables. Aucune option d'accès à une base existante."""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
E3 = "d1e70493a3d746158a53140d9d48083ca3c24794"
E2 = "bd742d15890deb6e4263b4029279fc7281196790"
DEMO = "backend/src/main/java/com/chifaa/backend/compatibility/DemoAccounts.java"
V1 = "backend/src/main/resources/db/compatibility/V1__local_dependencies.sql"
V2 = "backend/src/main/resources/db/migration/V2__student3_modules.sql"
TABLES = ("users", "staff", "kb_items", "chat_sessions", "messages", "safety_events", "organisations",
          "organisation_languages", "access_log", "flyway_schema_history")


def require(condition, detail):
    if not condition:
        raise RuntimeError(detail)


def call(args, *, env=None, data=None, expected_error=None):
    result = subprocess.run([str(a) for a in args], input=data, capture_output=True, env=env)
    if expected_error:
        require(result.returncode != 0 and expected_error in result.stderr.decode("utf-8", "replace"),
                "Échec attendu absent : " + expected_error)
    elif result.returncode:
        raise RuntimeError(Path(args[0]).name + " : " + (result.stderr + result.stdout).decode("utf-8", "replace")[-2500:])
    return result.stdout


def export(repo, revision, filename, target):
    target.write_bytes(call(["git", "-C", repo, "show", revision + ":" + filename]))


def classpath(repo, temp):
    report = repo / "backend/target/surefire-reports/TEST-tn.chifaa.clinicalreview.ClinicalReviewApiTest.xml"
    if report.exists():
        cp = ET.parse(report).find(".//property[@name='java.class.path']").get("value")
        if all(Path(j).exists() for j in cp.split(os.pathsep)):
            return cp
    dest = temp / "dependencies.txt"
    call([shutil.which("mvn"), "-f", repo / "backend/pom.xml", "dependency:build-classpath",
          "-Dmdep.outputFile=" + str(dest)])
    return dest.read_text().strip()


@contextmanager
def cluster(binaries, temp):
    # pg_ctl cannot inherit a live server's options. Use a fresh password and loopback-only listener.
    env = {k:v for k,v in os.environ.items() if not k.upper().startswith("PG")}
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    env.update(PGHOST="127.0.0.1", PGPORT=str(port), PGUSER="rehearsal", PGPASSWORD=secrets.token_urlsafe(32),
               PGCLIENTENCODING="UTF8", PGTZ="UTC", PGDATABASE="postgres", PGCONNECT_TIMEOUT="5")
    pw = temp / "init.secret"
    pw.write_text(env["PGPASSWORD"] + "\n", encoding="ascii")
    data = temp / "cluster"
    call([binaries / "initdb", "-D", data, "-A", "scram-sha-256", "-U", env["PGUSER"],
          "--pwfile", pw, "--encoding=UTF8", "--locale=C"], env=env)
    pw.unlink()
    ctl = [binaries / "pg_ctl", "-D", data, "-w"]
    # On Windows the new postmaster may inherit a capture pipe and keep it open.
    with (temp / "startup.log").open("wb") as log:
        subprocess.run([*map(str, ctl), "-l", str(temp / "postgres.log"), "-o",
                        f"-h 127.0.0.1 -p {port}", "start"], check=True, env=env,
                       stdin=subprocess.DEVNULL, stdout=log, stderr=log)
    try:
        yield env
    finally:
        if (data / "postmaster.pid").exists():
            call([*ctl, "-m", "fast", "stop"], env=env)


def rehearse(repo, binaries, temp, env):
    def sql(database, query, error=None):
        if isinstance(query, Path):
            query = query.read_text(encoding="utf-8")
        return call([binaries / "psql", "-XqAt", "-v", "ON_ERROR_STOP=1", "-d", database],
                    data=query.encode("utf-8"), env=env, expected_error=error).decode("utf-8").strip()

    def fingerprint(db):
        rows = {}
        for table in TABLES:
            rows[table] = sql(db, f"""SELECT count(*) || '/' || md5(coalesce(string_agg(h,'' ORDER BY h),''))
                FROM (SELECT md5(to_jsonb(t)::text) h FROM public.{table} t) s""")
        for seq in ("organisation_number","access_log_log_id_seq"):
            rows[seq] = sql(db, f"SELECT last_value,is_called FROM public.{seq}")
        return rows

    def counts(db, schema, tables):
        return {table:int(sql(db, f"SELECT count(*) FROM {schema}.{table}")) for table in tables}

    src, dst, classes = [temp / name for name in ("e3_ddl","e2_ddl","classes")]
    for folder in (src, dst, classes):
        folder.mkdir()
    for name in (V1, V2):
        export(repo, E3, name, src / Path(name).name)
    e2_sha = call(["git", "-C", repo, "rev-parse", E2 + "^{commit}"]).decode().strip()
    migrations = call(["git", "-C", repo, "ls-tree", "-r", "--name-only", e2_sha,
                       "backend/src/main/resources/db/migration"]).decode().splitlines()
    for name in migrations:
        export(repo, e2_sha, name, dst / Path(name).name)
    export(repo, E3, DEMO, temp / "DemoAccounts.java")
    export(repo, E3, "database/demo.sql", temp / "demo.sql")
    cp = classpath(repo, temp)
    call(["javac", "-encoding", "UTF-8", "-cp", cp, "-d", classes, HERE / "RehearsalFlyway.java", temp / "DemoAccounts.java"])
    def flyway(schema, db, folder):
        call(["java", "-cp", str(classes) + os.pathsep + cp, "com.chifaa.backend.compatibility.RehearsalFlyway",
              schema, db, folder], env=env)

    sql("postgres", "CREATE DATABASE original")
    print("Reconstruction de la base source par Flyway.", flush=True)
    flyway("public", "original", src)
    sql("original", temp / "demo.sql")
    original = fingerprint("original")
    # The demo deliberately ignores duplicate PKs. Confirm counts and payloads on replay.
    sql("original", temp / "demo.sql")
    require(original == fingerprint("original"), "Le rejeu de la démo a modifié la source")
    source_counts = counts("original", "public", TABLES)
    require(list(source_counts.values()) == [2,3,6,25,500,4,30,60,0,2], "Volumétrie E3 inattendue")
    dump = temp / "original.dump"
    call([binaries / "pg_dump", "-Fc", "-d", "original", "-f", dump], env=env)
    results = {"postgres":sql("original", "SHOW server_version"),"source_commit":E3,"target_commit":e2_sha,
               "source":source_counts,"scenarios":{}}

    for db in ("demo","edge"):
        print("Restauration et contrôles du scénario " + db, flush=True)
        sql("postgres", "CREATE DATABASE " + db)
        call([binaries / "pg_restore", "--exit-on-error", "--single-transaction", "-d", db, dump], env=env)
        require(original == fingerprint(db), "Restauration différente de la source")
        if db == "edge":
            sql(db, HERE / "edge-fixture.sql")
        snapshot = fingerprint(db)
        sql(db, HERE / "candidate.sql")
        flyway("e2", db, dst)
        sql(db, HERE / "demo-mappings.sql")
        if db == "edge":
            sql(db, """INSERT INTO migration_e3.kb_release VALUES (1,'KB-20260101');
                INSERT INTO migration_e3.reply_evidence
                SELECT a.message_id,q.message_id,'Knowledge' FROM public.messages a JOIN public.messages q
                USING(session_id,request_id) WHERE a.sender_role='AI' AND q.sender_role='User' AND a.content_id IS NOT NULL;""")
            sql(db, "DELETE FROM migration_e3.trigger_map")
            sql(db, HERE / "transfer.sql", "Missing migration mapping")
            sql(db, "INSERT INTO migration_e3.trigger_map VALUES ('DEMO_DISTRESS','INVALID_TEST_TYPE')")
            sql(db, HERE / "transfer.sql", "safety_events_trigger_type_check")
            for table in TABLES[:8]:
                require(sql(db, f"SELECT count(*) FROM e2.{table}") == "0", "Rollback incomplet : " + table)
            require(sql(db, "SELECT count(*) FROM migration_e3.held_sessions") == "0", "Archives partielles")
            sql(db, "UPDATE migration_e3.trigger_map SET new_type='TEST_TRIGGER_1'")
        sql(db, HERE / "transfer.sql")
        checks = sql(db, HERE / "verify.sql").splitlines()
        require(checks and all(row.endswith("|t") for row in checks), "Réconciliation : " + str(checks))
        target = counts(db, "e2", TABLES[:8])
        archived = int(sql(db, "SELECT count(*) FROM migration_e3.archived_messages"))
        require(archived == 500 and sql(db,"SELECT count(*) FROM migration_e3.held_sessions") == "25",
                "Historique de démonstration incomplet")
        require(int(sql(db, "SELECT count(user_feedback) FROM migration_e3.archived_messages")) == 250, "Notes archivées")
        require(target == dict(zip(TABLES[:8], [2,3,6,25+(db=="edge"),2*(db=="edge"),4,30,60])), "Volumétrie cible")
        require(sql(db,"SELECT count(*) FROM e2.kb_items WHERE approval_status='Draft' AND is_fictional")=="6", "Brouillons")
        require(sql(db,"SELECT nextval('e2.organisation_number')")=="31","Séquence annuaire")
        require(sql(db,"SELECT count(*) FROM e2.flyway_schema_history WHERE success AND type='SQL'")=="4","Historique Flyway cible")
        if db == "edge":
            require(sql(db,"SELECT count(*) FROM migration_e3.audit_actors WHERE staff_code='TEST-CT' AND resource_id='ORG-001'")=="1","Auteur d’audit")
            require(sql(db,"SELECT count(*) FROM e2.messages WHERE reply_kind='Knowledge' AND kb_version='KB-20260101' AND user_feedback=4 AND content_id IS NOT NULL AND reply_to IS NOT NULL")=="1","Provenance")
            require(sql(db,"SELECT count(*) FROM e2.safety_events WHERE reviewer_id='TEST-SR' AND org_id='ORG-001' AND action_taken='Referral'")=="1","Référent d’alerte")
        sql(db, HERE / "transfer.sql", "Candidate must be empty")
        require(snapshot == fingerprint(db), "Mutation du schéma source")
        require(target == counts(db, "e2", TABLES[:8]), "Le rejeu a modifié les volumes")
        results["scenarios"][db] = {"candidate":target,"archived_messages":archived,"archived_ratings":250,
            "held_sessions":25,"visible_kb":0,"source_intact":True,"verified_checks":len(checks),
            "duplicate_run_rejected":True,"missing_mapping_and_rollback_tested":db=="edge"}
    require(original == fingerprint("original"), "La base source a changé")
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True, help="Dépôt Git contenant les deux commits")
    parser.add_argument("--pg-bin", type=Path, required=True, help="Dossier bin de PostgreSQL")
    parser.add_argument("--rapport", type=Path, help="Écrire les résultats agrégés en JSON")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="chifaa-migration-") as directory:
        temp = Path(directory)
        with cluster(args.pg_bin.resolve(), temp) as environment:
            report = rehearse(args.repo.resolve(), args.pg_bin.resolve(), temp, environment)
    summary = json.dumps(report, ensure_ascii=False, indent=2)
    if args.rapport:
        args.rapport.write_text(summary + "\n", encoding="utf-8")
    print(summary)
