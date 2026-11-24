package tn.chifaa.auth;

import java.util.Locale;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.chifaa.users.Profile;

@Configuration
@org.springframework.context.annotation.Profile("demo-seed")
class DemoPatients {
    @Bean ApplicationRunner createDemoPatients(Accounts accounts,PhoneVault vault,JdbcTemplate db) {
        return args->{
            for (int i=0;i<200;i++) {
                // Reserved fictional North American 555-0100 through 555-0199 in two area codes.
                String phone=String.format(Locale.ROOT,"+1%s55501%02d",i<100?"202":"212",i%100);
                String hash=vault.fingerprint(phone);
                if (Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from identities where phone_hash=?)",Boolean.class,hash))) continue;
                var profile=new Profile(i%2==0?"18–29":"30–39",i%2==0?"TN-11":"TN-31",i%2==0?"French":"MSA");
                var info=new OtpService.Registration(vault.encrypt(phone),profile,i%2==0,i%3==0,"v1.0");
                accounts.authenticate(new OtpService.Ticket(hash,info));
            }
        };
    }
}
