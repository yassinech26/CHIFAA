package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class LoginActivity extends PublicPage {
    @Override int layout() { return R.layout.patient_login; }
    @Override void bind() {

        click(R.id.send_code,()->{
            String phone=input(R.id.phone).replace(" ","");
            if (!phone.matches("\\+[1-9][0-9]{7,14}")) { notice(R.string.invalid); return; }
            request(api.login(new Models.Login(phone)),this::verify);
        });
    }
    private void verify(Models.Challenge c) {
        startActivity(new Intent(this,OtpVerificationActivity.class).putExtra("challenge",c.challengeId));
    }
}
