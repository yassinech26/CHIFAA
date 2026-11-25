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
abstract class DeviceCheck extends PostgresJwtTest {
    private static final String APP = "com.example.chifaaapp";
    private final String adb, serial;

    protected DeviceCheck(String adb,String serial) { this.adb=adb; this.serial=serial; }

    private String adb(byte[] input,String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(adb,"-s",serial));
        command.addAll(Arrays.asList(args));
        Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var out = child.getOutputStream()) { if (input != null) out.write(input); }
        String output = new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if (child.waitFor()!=0) throw new IOException("ADB failed: "+args[0]);
        return output;
    }

    protected final void run() throws Exception {
        Path dir = Files.createTempDirectory("chifaa-device-");
        Path p12=dir.resolve("tls.p12"), cert=dir.resolve("ca.pem");
        byte[] key = new byte[24]; new SecureRandom().nextBytes(key);
        String password = Base64.getUrlEncoder().encodeToString(key);
        String keytool = Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString();
        try {
            keytool(keytool,"-genkeypair","-alias","device","-keystore",p12.toString(),"-storepass",password,
                    "-keyalg","RSA","-keysize","2048","-storetype","PKCS12","-validity","2","-dname","CN=localhost",
                    "-ext","SAN=dns:localhost","-noprompt");
            keytool(keytool,"-exportcert","-alias","device","-keystore",p12.toString(),"-storepass",password,
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
                int port = context.getWebServer().getPort();
                String tcp = "tcp:"+port;
                adb(null,"reverse",tcp,tcp);
                try {
                    exercise(db, "https://localhost:"+port+"/", Files.readString(cert));
                } finally {
                    adb(null,"reverse","--remove",tcp);
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
    protected abstract void exercise(JdbcTemplate db, String url, String certificate) throws Exception;

    protected final void instrument(Map<String,String> fixture, String config, String flag, String test) throws Exception {
        try {
            adb(new ObjectMapper().writeValueAsBytes(fixture),"shell","run-as "+APP+" sh -c 'mkdir -p cache && cat > "+config+"'");
            String run = adb(null,"shell","am","instrument","-w","-r","-e",flag,"required","-e","class",
                    APP+"."+test,APP+".test/androidx.test.runner.AndroidJUnitRunner");
            if (!run.contains("OK (1 test)") || !run.contains("INSTRUMENTATION_STATUS_CODE: 0")
                    || run.contains("FAILURES!!!") || run.contains("INSTRUMENTATION_STATUS_CODE: -")) {
                throw new AssertionError("Échec du test sur appareil.\n"+run);
            }
            System.out.println(test+" HTTPS: OK (1 test)");
        } finally {
            adb(null,"shell","run-as",APP,"rm","-f",config);
        }
    }
}
