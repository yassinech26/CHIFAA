package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class CheckinDetailsActivity extends HealthPage {
    @Override int layout() { return R.layout.patient_details; }
    @Override void refresh() {
        request(api.one(getIntent().getStringExtra("id")),entry->{
            value(R.id.result,getString(R.string.count_effects,entry.date,entry.effects.size())+"\n"+android.text.TextUtils.join("\n",entry.effects));
            contentVisible(true);
        });
    }
}
