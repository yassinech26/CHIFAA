package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class AgeVerificationActivity extends PublicPage {
    @Override int layout() { return R.layout.patient_age; }
    @Override void bind() {
        click(R.id.adult_yes,()->{ getIntent().putExtra("adult",true); go(ServiceConsentActivity.class); });
        click(R.id.adult_no,()->notice(R.string.minor));
    }
}
