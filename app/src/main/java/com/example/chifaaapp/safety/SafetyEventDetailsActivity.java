package com.example.chifaaapp.safety;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.example.chifaaapp.R;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SafetyEventDetailsActivity extends SafetyScreen {
    private UUID id;
    private SafetyLabels labels;
    private SafetyRepository.Event event;
    private EditText reviewer, org;
    private Spinner status, action, outcome;
    private List<String> permittedStates;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        layout(R.layout.b3_detail);
        labels = new SafetyLabels(this);
        try { id = UUID.fromString(getIntent().getStringExtra("event")); }
        catch (RuntimeException invalid) { finish(); }
        reviewer = findViewById(R.id.b3_reviewer);
        org = findViewById(R.id.b3_org);
        status = findViewById(R.id.b3_status);
        action = findViewById(R.id.b3_action);
        outcome = findViewById(R.id.b3_outcome);
        findViewById(R.id.b3_back).setOnClickListener(v -> finish());
        findViewById(R.id.b3_save).setOnClickListener(v -> save());
    }

    @Override protected void clear() {
        event = null;
        findViewById(R.id.b3_content).setVisibility(View.GONE);
        for (int field : new int[]{R.id.b3_trigger,R.id.b3_meta,R.id.b3_ids,R.id.b3_times,R.id.b3_delays,R.id.b3_summary})
            text(field,"");
        reviewer.setText("");
        org.setText("");
        status.setAdapter(null);
        action.setAdapter(null);
        outcome.setAdapter(null);
        findViewById(R.id.b3_reload).setEnabled(false);
        note(0);
    }

    @Override protected void reload() {
        clear();
        if (id == null || !session.generation.equals(getIntent().getStringExtra("generation"))) { finish(); return; }
        findViewById(R.id.b3_reload).setEnabled(true);
        String filter = getIntent().getStringExtra("filter");
        int offset = getIntent().getIntExtra("offset",0);
        locate(filter,offset,filter != null || offset != 0);
    }

    // Le GET B3 renvoie les détails complets. Vérifier d’abord la page qui a ouvert l’écran.
    private void locate(String filter, int offset, boolean fromQueuePage) {
        int size = 100;
        fetch(session.api.list(filter,offset,size), list -> {
            for (SafetyRepository.Event item : list) {
                if (id.equals(item.eventId)) { show(item); return; }
            }
            if (fromQueuePage) locate(null,0,false);
            else if (list.size() == size) locate(null,offset+size,false);
            else note(R.string.b3_missing);
        }, code -> note(R.string.b3_error));
    }

    private void text(int field, String value) { ((TextView)findViewById(field)).setText(value); }

    private void show(SafetyRepository.Event item) {
        event = item;
        text(R.id.b3_trigger, item.triggerType);
        text(R.id.b3_meta, labels.meta(item));
        text(R.id.b3_ids, getString(R.string.b3_ids,item.eventId,labels.optional(item.userPid),labels.optional(item.sessionId)));
        text(R.id.b3_times, getString(R.string.b3_times,labels.date(item.flaggedAt),labels.date(item.takenInChargeAt),
                labels.date(item.resolvedAt)));
        text(R.id.b3_delays, getString(R.string.b3_delays, labels.delay(item.handlingDelaySeconds),
                labels.delay(item.resolutionDelaySeconds)));
        int index = SafetyLabels.STATES.indexOf(item.status);
        boolean editable = index >= 0 && index < SafetyLabels.STATES.size()-1;
        findViewById(R.id.b3_content).setVisibility(View.VISIBLE);
        findViewById(R.id.b3_form).setVisibility(editable ? View.VISIBLE : View.GONE);
        findViewById(R.id.b3_summary).setVisibility(editable ? View.GONE : View.VISIBLE);
        findViewById(R.id.b3_reload).setEnabled(true);
        if (!editable) {
            text(R.id.b3_summary,getString(R.string.b3_summary,labels.optional(item.reviewerId),
                    labels.label(item.actionTaken,SafetyLabels.ACTIONS,R.array.b3_actions),
                    labels.label(item.referralOutcome,SafetyLabels.OUTCOMES,R.array.b3_outcomes), labels.optional(item.orgId)));
            return;
        }
        permittedStates = SafetyLabels.STATES.subList(index,index+2);
        List<String> display = new ArrayList<>();
        for (String value : permittedStates) display.add(labels.label(value,SafetyLabels.STATES,R.array.b3_states));
        labels.options(status,display,0);
        labels.withEmpty(action,R.string.b3_none,R.array.b3_actions,SafetyLabels.ACTIONS.indexOf(item.actionTaken)+1);
        labels.withEmpty(outcome,R.string.b3_none,R.array.b3_outcomes,SafetyLabels.OUTCOMES.indexOf(item.referralOutcome)+1);
        reviewer.setText(item.reviewerId);
        org.setText(item.orgId);
        editEnabled(true);
    }

    private void editEnabled(boolean enabled) {
        for (int field : new int[]{R.id.b3_status,R.id.b3_reviewer,R.id.b3_action,R.id.b3_outcome,R.id.b3_org,R.id.b3_save})
            findViewById(field).setEnabled(enabled);
        findViewById(R.id.b3_reload).setEnabled(enabled);
    }

    private static String selected(Spinner spinner, List<String> codes) {
        int index = spinner.getSelectedItemPosition();
        return index <= 0 ? null : codes.get(index-1);
    }

    private static String entered(EditText field) {
        String value = field.getText().toString().trim();
        return value.isEmpty() ? null : value;
    }

    private static void change(JsonObject patch, String key, String previous, String value) {
        if (Objects.equals(previous,value)) return;
        if (value == null) patch.add(key, JsonNull.INSTANCE);
        else patch.addProperty(key,value);
    }

    private void save() {
        if (!authorized() || event == null) return;
        JsonObject patch = new JsonObject();
        change(patch,"status",event.status,permittedStates.get(status.getSelectedItemPosition()));
        change(patch,"reviewerId",event.reviewerId,entered(reviewer));
        change(patch,"actionTaken",event.actionTaken,selected(action,SafetyLabels.ACTIONS));
        change(patch,"referralOutcome",event.referralOutcome,selected(outcome,SafetyLabels.OUTCOMES));
        change(patch,"orgId",event.orgId,entered(org));
        if (patch.size() == 0) { note(R.string.b3_unchanged); return; }
        editEnabled(false);
        note(0);
        fetch(session.api.update(id,patch), updated -> {
            if (!id.equals(updated.eventId)) { uncertain(R.string.b3_uncertain); return; }
            show(updated);
            note(R.string.b3_confirmed);
        }, code -> {
            if (code == 400) { editEnabled(true); note(R.string.b3_invalid); }
            else if (code == 409) uncertain(R.string.b3_conflict);
            else if (code == 404) uncertain(R.string.b3_missing);
            else uncertain(R.string.b3_uncertain);
        });
    }

    private void uncertain(int message) {
        clear();
        findViewById(R.id.b3_reload).setEnabled(true);
        note(message);
    }
}
