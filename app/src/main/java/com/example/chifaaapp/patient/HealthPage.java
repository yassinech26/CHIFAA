package com.example.chifaaapp.patient;

import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import com.example.chifaaapp.R;

abstract class HealthPage extends PatientActivity {
    // Every entry or return from background obtains fresh authorized data before revealing controls.
    @Override protected void onStart() {
        contentVisible(false);
        super.onStart();
    }
    void contentVisible(boolean visible) {
        LinearLayout page=(LinearLayout)((ViewGroup)findViewById(R.id.patient_page)).getChildAt(0);
        for (int i=1;i<page.getChildCount()-1;i++) page.getChildAt(i).setVisibility(visible?View.VISIBLE:View.GONE);
    }
    @Override protected void onStop() {
        contentVisible(false);
        super.onStop();
        for (int id:new int[]{R.id.rows,R.id.effects,R.id.treatments}) {
            LinearLayout group=findViewById(id);
            if (group!=null) group.removeAllViews();
        }
        for (int id:new int[]{R.id.result,R.id.year,R.id.date}) {
            android.widget.TextView field=findViewById(id);
            if (field!=null) field.setText("");
        }
        for (int id:new int[]{R.id.cancer,R.id.phase,R.id.period,R.id.facility,R.id.interest}) {
            android.widget.Spinner field=findViewById(id);
            if (field!=null) { field.setAdapter(null); field.setTag(null); }
        }
    }
}
