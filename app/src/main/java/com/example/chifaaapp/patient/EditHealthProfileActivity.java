package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class EditHealthProfileActivity extends HealthPage {
    @Override int layout() { return R.layout.patient_edit_health; }
    @Override void refresh() {
        request(api.catalog(),c->request(api.health(),p->{
            options(R.id.cancer,array(R.array.cancer_codes),array(R.array.cancer_labels),p.cancerType,R.string.skip);
            options(R.id.period,array(R.array.period_codes),array(R.array.period_labels),p.diagnosisPeriodBand,R.string.skip);
            options(R.id.facility,array(R.array.facility_codes),array(R.array.facility_labels),p.facilityType,R.string.skip);
            options(R.id.interest,array(R.array.interest_codes),array(R.array.interest_labels),String.valueOf(p.fertilityInfoInterest),R.string.skip);
            String[] phases=c.carePhases.toArray(new String[0]);
            options(R.id.phase,phases,phases,p.carePhase,R.string.skip);
            value(R.id.year,p.diagnosisYear==null?"":String.valueOf(p.diagnosisYear));
            choices(R.id.treatments,Arrays.asList(array(R.array.treatments_codes)),Arrays.asList(array(R.array.treatments_labels)),p.treatments);
            contentVisible(true); if (phases.length==0) notice(R.string.catalog_wait);
            click(R.id.save,this::save);
        }));
    }
    private void save() {
        var p=new Models.Health(); p.cancerType=selection(R.id.cancer); p.carePhase=selection(R.id.phase);
        p.diagnosisPeriodBand=selection(R.id.period); p.facilityType=selection(R.id.facility);
        String year=input(R.id.year);
        if (!year.isEmpty() && !year.matches("[0-9]{4}")) { notice(R.string.invalid); return; }
        p.diagnosisYear=year.isEmpty()?null:Integer.valueOf(year);
        String interest=selection(R.id.interest); p.fertilityInfoInterest=interest==null?null:Boolean.valueOf(interest);
        p.treatments=checked(R.id.treatments); request(api.save(p),ignored->finish());
    }
}
