package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class HealthProfileActivity extends HealthPage {
    @Override int layout() { return R.layout.patient_health_profile; }
    @Override void bind() { click(R.id.edit_health,()->go(EditHealthProfileActivity.class)); }
    @Override void refresh() {
        request(api.health(),p->{
            List<String> fields=new ArrayList<>();
            fields.add(getString(R.string.cancer)+" · "+label(p.cancerType,R.array.cancer_codes,R.array.cancer_labels));
            fields.add(getString(R.string.phase)+" · "+(p.carePhase==null?getString(R.string.skip):p.carePhase));
            fields.add(getString(R.string.period)+" · "+label(p.diagnosisPeriodBand,R.array.period_codes,R.array.period_labels));
            fields.add(getString(R.string.year)+" · "+(p.diagnosisYear==null?getString(R.string.skip):p.diagnosisYear));
            fields.add(getString(R.string.facility)+" · "+label(p.facilityType,R.array.facility_codes,R.array.facility_labels));
            fields.add(getString(R.string.interest)+" · "+label(String.valueOf(p.fertilityInfoInterest),R.array.interest_codes,R.array.interest_labels));
            for (String t:p.treatments) fields.add(label(t,R.array.treatments_codes,R.array.treatments_labels));
            value(R.id.result,android.text.TextUtils.join("\n",fields)); contentVisible(true);
        });
    }
}
