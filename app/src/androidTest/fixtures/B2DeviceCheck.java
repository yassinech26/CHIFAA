package tn.chifaa;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;

public class B2DeviceCheck extends DeviceCheck {
    private B2DeviceCheck(String adb, String serial) { super(adb, serial); }

    @Override protected void exercise(org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext context, JdbcTemplate db, String url, String certificate) throws Exception {
        db.update("insert into staff(staff_id,role,active) values ('CT-E2E','Content team',true),('CR-E2E','Clinical reviewer',true)");
        Map<String,String> fixture = Map.of("url",url,"certificate",certificate,
                            "editor",bearer("CT-E2E","chifaa-test").substring("Bearer ".length()),
                            "reviewer",bearer("CR-E2E","chifaa-test").substring("Bearer ".length()));
        instrument(fixture, "cache/b2-device-fixture.json", "b2Live", "safety.B2DeviceTest");
    }

    public static void main(String[] args) throws Exception { new B2DeviceCheck(args[0],args[1]).run(); }
}
