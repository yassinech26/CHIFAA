package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class ManageConsentsActivity extends PatientActivity {
    @Override int layout() { return R.layout.patient_manage_consents; }
    @Override void refresh() {
        request(api.consents(),list->{
            LinearLayout rows=findViewById(R.id.rows); rows.removeAllViews();
            String[] purposes={"Service use","Health data","Survey"};
            int[] names={R.string.service_use,R.string.health_data,R.string.survey};
            for (int i=0;i<purposes.length;i++) {
                String purpose=purposes[i];
                boolean given=list.stream().anyMatch(e->purpose.equals(e.purpose)&&"Given".equals(e.status));
                line(rows,getString(names[i])+" · "+getString(given?R.string.given:R.string.inactive));
                for (Models.Evidence e:list) if (purpose.equals(e.purpose))
                    line(rows,e.version+" · "+getString(statusLabel(e.status))+" · "+(e.withdrawnAt==null?e.givenAt:e.withdrawnAt));
                Button b=new Button(this); b.setText(given?R.string.withdraw:R.string.grant);
                int description=new int[]{R.string.service_notice,R.string.health_notice,R.string.survey_notice}[i];
                b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(b.getText()).setMessage(description)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(dialog,which)->
                        request(api.change(new Models.Change(purpose,!given)),ignored->{
                            if (given && "Service use".equals(purpose)) { repository.close(); reset(WelcomeActivity.class); }
                            else refresh();
                        })).show());
                rows.addView(b);
            }
        });
    }
    private int statusLabel(String status) {
        switch (status) {
            case "Given": return R.string.given;
            case "Expired": return R.string.consent_expired;
            case "Withdrawn": return R.string.withdrawn;
            default: return R.string.inactive;
        }
    }
}
