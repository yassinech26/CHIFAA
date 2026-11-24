package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class LanguageSettingsActivity extends PatientActivity {
    @Override int layout() { return R.layout.patient_language_settings; }
    @Override void bind() {
        click(R.id.french,()->locale("fr")); click(R.id.arabic,()->locale("ar"));
    }
    private void locale(String tag) {
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tag));
        finish();
    }
}
