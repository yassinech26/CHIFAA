package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class RegisterActivity extends PublicPage {
    @Override int layout() { return R.layout.patient_register; }
    @Override void bind() {
        profileFields(null);
        click(R.id.send_code,()->{
            String phone=input(R.id.phone).replace(" ","");
            if (!phone.matches("\\+[1-9][0-9]{7,14}")) { notice(R.string.invalid); return; }
            Models.Register r=new Models.Register();
            r.phone=phone; r.profile=profileInput();
            if (r.profile==null) return;
            r.adult=getIntent().getBooleanExtra("adult",false);
            r.service=getIntent().getBooleanExtra("service",false);
            r.health=getIntent().getBooleanExtra("health",false);
            r.survey=getIntent().getBooleanExtra("survey",false);
            request(api.register(r),this::verify);
        });
    }
    private void verify(Models.Challenge c) {
        startActivity(new Intent(this,OtpVerificationActivity.class).putExtra("challenge",c.challengeId));
    }
}
