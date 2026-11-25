package tn.chifaa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/** Lance le vrai backend sur loopback, provisionne le test instrumenté, puis ferme ses ressources. */
public class B2DeviceCheck extends PostgresJwtTest {
    private static final String APP = "com.example.chifaaapp";
    private static final String CONFIG = "cache/b2-device-fixture.json";
    private final String adb, serial;

    private B2DeviceCheck(String adb,String serial) { this.adb=adb; this.serial=serial; }

    private String adb(byte[] input,String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(adb,"-s",serial));
        command.addAll(Arrays.asList(args));
        Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var out = child.getOutputStream()) { if (input != null) out.write(input); }
        String output = new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if (child.waitFor()!=0) throw new IOException("ADB failed: "+args[0]);
        return output;
    }

    private void run() throws Exception {
        Path dir = Files.createTempDirectory("chifaa-b2-");
        Path p12=dir.resolve("tls.p12"), cert=dir.resolve("ca.pem");
        byte[] key = new byte[24]; new SecureRandom().nextBytes(key);
        String password = Base64.getUrlEncoder().encodeToString(key);
        String keytool = Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString();
        try {
            keytool(keytool,"-genkeypair","-alias","b2","-keystore",p12.toString(),"-storepass",password,
                    "-keyalg","RSA","-keysize","2048","-storetype","PKCS12","-validity","2","-dname","CN=localhost",
                    "-ext","SAN=dns:localhost","-noprompt");
            keytool(keytool,"-exportcert","-alias","b2","-keystore",p12.toString(),"-storepass",password,
                    "-rfc","-file",cert.toString());
            for (String prop : PostgresJwtTest.class.getAnnotation(SpringBootTest.class).properties()) {
                String[] pair = prop.split("=",2); System.setProperty(pair[0],pair[1]);
            }
            PostgresJwtTest.signingKeys((name,supplier) -> System.setProperty(name,supplier.get().toString()));
            SpringApplication app = new SpringApplication(ChifaaApplication.class, PostgresJwtTest.Database.class);
            app.setDefaultProperties(Map.of("server.address","127.0.0.1","server.port","0","server.ssl.enabled","true",
                    "server.ssl.key-store",p12.toUri().toString(),"server.ssl.key-store-password",password,
                    "server.ssl.key-store-type","PKCS12","spring.main.banner-mode","off"));
            try (var context = (ServletWebServerApplicationContext)app.run()) {
                JdbcTemplate db = new JdbcTemplate(context.getBean(DataSource.class));
                db.update("insert into staff(staff_id,role,active) values ('CT-E2E','Content team',true),('CR-E2E','Clinical reviewer',true)");
                int port = context.getWebServer().getPort();
                String tcp = "tcp:"+port;
                adb(null,"reverse",tcp,tcp);
                try {
                    Map<String,String> fixture = Map.of("url","https://localhost:"+port+"/","certificate",Files.readString(cert),
                            "editor",bearer("CT-E2E","chifaa-test").substring("Bearer ".length()),
                            "reviewer",bearer("CR-E2E","chifaa-test").substring("Bearer ".length()));
                    adb(new ObjectMapper().writeValueAsBytes(fixture),"shell","run-as "+APP+" sh -c 'mkdir -p cache && cat > "+CONFIG+"'");
                    String run = adb(null,"shell","am","instrument","-w","-r","-e","b2Live","required","-e","class",
                            APP+".safety.B2DeviceTest",APP+".test/androidx.test.runner.AndroidJUnitRunner");
                    if (!run.contains("OK (1 test)") || !run.contains("INSTRUMENTATION_STATUS_CODE: 0")
                            || run.contains("FAILURES!!!") || run.contains("INSTRUMENTATION_STATUS_CODE: -")) {
                        throw new AssertionError("Le test sur appareil a échoué.\n"+run);
                    }
                    System.out.println("B2 DEVICE HTTPS: OK (1 test)");
                } finally {
                    try { adb(null,"shell","run-as",APP,"rm","-f",CONFIG); }
                    finally { adb(null,"reverse","--remove",tcp); }
                }
            }
        } finally {
            PostgresJwtTest.closeKeys();
            Files.deleteIfExists(cert); Files.deleteIfExists(p12); Files.deleteIfExists(dir);
        }
    }
    private static void keytool(String... command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        if (p.waitFor()!=0) throw new IOException("Génération du certificat local impossible");
    }
    public static void main(String[] args) throws Exception { new B2DeviceCheck(args[0],args[1]).run(); }
}
