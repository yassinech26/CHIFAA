package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class ProfileActivity extends PatientActivity {
    @Override int layout() { return R.layout.patient_profile; }
    @Override void bind() {
        click(R.id.edit_profile,()->go(EditProfileActivity.class));
        click(R.id.language_settings,()->go(LanguageSettingsActivity.class));
        click(R.id.health_home,()->go(HealthHomeActivity.class));
        click(R.id.my_data,()->go(MyDataActivity.class));
        click(R.id.sign_out,()->request(api.logout(),ignored->{ repository.authenticate(null); reset(WelcomeActivity.class); }));
    }
    @Override void refresh() {
        show(R.id.health_home,false);
        request(api.profile(),p->{
            value(R.id.result,p.ageBand+" · "+label(p.governorate,R.array.governorate_codes,R.array.governorate_labels)
                +" · "+label(p.preferredLanguage,R.array.language_codes,R.array.language_labels));
            request(api.consents(),list->show(R.id.health_home,list.stream().anyMatch(e->"Health data".equals(e.purpose)&&"Given".equals(e.status))));
        });
    }
}
