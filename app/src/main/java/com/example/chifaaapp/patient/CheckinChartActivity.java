package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class CheckinChartActivity extends HealthPage {
    @Override int layout() { return R.layout.patient_chart; }
    @Override void refresh() {
        request(api.history(),entries->{
            LinearLayout rows=findViewById(R.id.rows); rows.removeAllViews();
            if (entries.isEmpty()) line(rows,getString(R.string.empty));
            for (int i=entries.size()-1;i>=0;i--) {
                var e=entries.get(i); line(rows,getString(R.string.count_effects,e.date,e.effects.size()));
                ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                bar.setMax(11); bar.setProgress(e.effects.size());
                bar.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                rows.addView(bar);
            }
            contentVisible(true);
        });
    }
}
