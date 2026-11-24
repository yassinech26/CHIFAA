package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class HealthConsentActivity extends PublicPage {
    @Override int layout() { return R.layout.patient_health_consent; }
    @Override void bind() {
        click(R.id.agree,()->choose(true));
        click(R.id.decline,()->choose(false));
    }
    private void choose(boolean agree) {
        getIntent().putExtra("health",agree);
        go(SurveyConsentActivity.class);
    }
}
