package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class ServiceConsentActivity extends PublicPage {
    @Override int layout() { return R.layout.patient_service; }
    @Override void bind() {
        click(R.id.agree,()->choose(true));
        click(R.id.decline,()->notice(R.string.required_service));
    }
    private void choose(boolean agree) {
        getIntent().putExtra("service",agree);
        go(HealthConsentActivity.class);
    }
}
