package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class WelcomeActivity extends PublicPage {
    @Override int layout() { return R.layout.patient_welcome; }
    @Override void bind() {
        click(R.id.sign_up,()->go(AgeVerificationActivity.class));
        click(R.id.sign_in,()->go(LoginActivity.class));
    }
    @Override void refresh() { if (repository.signedIn()) reset(ProfileActivity.class); }
}
