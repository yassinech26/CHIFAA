package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class CreateCheckinActivity extends HealthPage {
    @Override int layout() { return R.layout.patient_create_checkin; }
    @Override void refresh() {
        request(api.catalog(),c->{
            java.text.SimpleDateFormat iso=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            value(R.id.date,iso.format(new Date()));
            choices(R.id.effects,c.sideEffects,c.sideEffects,Collections.emptyList());
            contentVisible(true);
            if (c.sideEffects.isEmpty()) { show(R.id.save,false); notice(R.string.catalog_wait); return; }
            click(R.id.save,()->{
                Models.Checkin form=new Models.Checkin();
                form.date=input(R.id.date); form.effects=checked(R.id.effects);
                if (!form.date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) { notice(R.string.invalid); return; }
                request(api.create(form),entry->{
                    startActivity(new Intent(this,CheckinDetailsActivity.class).putExtra("id",entry.id)); finish();
                });
            });
        });
    }
}
