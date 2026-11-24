package com.example.chifaaapp.patient;

import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.*;
import com.example.chifaaapp.R;
import java.util.*;

public class HealthHomeActivity extends HealthPage {
    @Override int layout() { return R.layout.patient_health_home; }
    @Override void bind() {
        click(R.id.health_profile,()->go(HealthProfileActivity.class));
        click(R.id.create_checkin,()->go(CreateCheckinActivity.class));
        click(R.id.history,()->go(CheckinHistoryActivity.class));
        click(R.id.chart,()->go(CheckinChartActivity.class));
        click(R.id.fertility,()->go(FertilityInfoActivity.class));
    }
    @Override void refresh() {
        request(api.health(),p->{ contentVisible(true); show(R.id.fertility,Boolean.TRUE.equals(p.fertilityInfoInterest)); });
    }
}
