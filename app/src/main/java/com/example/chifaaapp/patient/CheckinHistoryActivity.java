package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class CheckinHistoryActivity extends HealthPage {
    @Override int layout() { return R.layout.patient_history; }
    @Override void refresh() {
        request(api.history(),entries->{
            LinearLayout rows=findViewById(R.id.rows); rows.removeAllViews();
            if (entries.isEmpty()) line(rows,getString(R.string.empty));
            for (var entry:entries) {
                Button b=new Button(this); b.setText(getString(R.string.count_effects,entry.date,entry.effects.size()));
                b.setOnClickListener(v->startActivity(new Intent(this,CheckinDetailsActivity.class).putExtra("id",entry.id)));
                rows.addView(b);
            }
            contentVisible(true);
        });
    }
}
