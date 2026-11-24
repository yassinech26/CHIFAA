package com.example.chifaaapp.admin.safety;

import static com.example.chifaaapp.local.ApiRepository.*;
import static com.example.chifaaapp.directory.DirectoryLabels.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import com.example.chifaaapp.R;
import com.example.chifaaapp.local.LocalActivity;
import com.google.gson.*;
import java.util.*;

public class SafetyQueueActivity extends LocalActivity {
    private Spinner severity, status;
    private int page;
    private final List<JsonObject> events = new ArrayList<>();
    private com.google.android.material.button.MaterialButton more;
    private static final int[] SEVERITY_COLORS = {R.color.text_secondary,R.color.warning,R.color.e3_high,R.color.error};

    @Override protected int title() { return R.string.e3_queue; }

    @Override protected void setup(Bundle state) {
        severity=options(ui.header,R.string.e3_severity,withAll(this,R.array.e3_severity_labels),0);
        status=options(ui.header,R.string.e3_status,withAll(this,R.array.e3_status_labels),0);
        var filters = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { load(0); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        severity.setOnItemSelectedListener(filters);
        status.setOnItemSelectedListener(filters);
        button(ui.header,R.string.e3_refresh,() -> load(0));
        more=button(ui.footer,R.string.e3_older,() -> load(page));
    }

    @Override protected void onStart() { super.onStart(); load(0); }

    private void load(int current) {
        String level=filter(SEVERITIES,severity.getSelectedItemPosition());
        String state=filter(STATUSES,status.getSelectedItemPosition());
        model.run(() -> {
            var result=new JsonObject();
            result.addProperty("page",current);
            result.addProperty("severity",level);
            result.addProperty("status",state);
            result.add("events",execute(API.events(level,state,current)));
            return result;
        });
    }

    @Override protected void onResult(Object result) {
        var response=(JsonObject)result;
        if (!value(response,"severity").equals(filter(SEVERITIES,severity.getSelectedItemPosition()))
                || !value(response,"status").equals(filter(STATUSES,status.getSelectedItemPosition()))) {
            load(0);
            return;
        }
        page=response.get("page").getAsInt()+1;
        if (page==1) events.clear();
        var items=response.getAsJsonArray("events");
        more.setVisibility(items.size()==50 ? View.VISIBLE : View.GONE);
        for (var item:items) events.add(item.getAsJsonObject());
        var rows=new ArrayList<java.util.function.Consumer<LinearLayout>>();
        for (var event:events) rows.add(card -> {
            String id=value(event,"id");
            text(card,id,true);
            String level=value(event,"severity");
            int index=index(SEVERITIES,level);
            var risk=text(card,label(this,R.array.e3_severity_labels,SEVERITIES,level),true);
            if (index>=0) {
                var indicator = new android.graphics.drawable.GradientDrawable();
                indicator.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                indicator.setColor(color(SEVERITY_COLORS[index]));
                int size = dim(R.dimen.e3_gap);
                indicator.setBounds(0,0,size,size);
                risk.setCompoundDrawablesRelative(indicator,null,null,null);
                risk.setCompoundDrawablePadding(size);
            }
            text(card,label(this,R.array.e3_status_labels,STATUSES,value(event,"status")),false);
            text(card,getString(R.string.e3_event_date,displayTimestamp(value(event,"flaggedAt"))),false);
            text(card,value(event,"reviewerId"),false);
            button(card,R.string.e3_detail,() -> open(SafetyEventDetailsActivity.class,id));
        });
        show(rows);
        notice(rows.isEmpty()?getString(R.string.e3_empty):"");
    }
}
