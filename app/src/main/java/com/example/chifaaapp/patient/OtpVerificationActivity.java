package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class OtpVerificationActivity extends PublicPage {
    @Override int layout() { return R.layout.patient_otp; }
    @Override void bind() {
        click(R.id.verify,()->{
            String code=input(R.id.otp_code);
            if (!code.matches("[0-9]{6}")) { notice(R.string.invalid); return; }
            request(api.verify(new Models.Verify(getIntent().getStringExtra("challenge"),code)),session->{
                repository.authenticate(session.token); reset(ProfileActivity.class);
            });
        });
    }
}
