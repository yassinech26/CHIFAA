package com.example.chifaaapp.clinicalreview;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.*;
import com.example.chifaaapp.R;
import com.example.chifaaapp.safety.SafetyScreen;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;

abstract class ReviewScreen extends SafetyScreen {
    static final String ID = "review.contentId", SESSION = "review.session";
    static final List<String> STATES = Arrays.asList("Draft","Clinically approved","Retired");
    static final List<String> LANG = Arrays.asList("French","MSA","Darija");
    static final List<String> EVIDENCE = Arrays.asList(null,"High","Moderate","Low","Expert opinion");
    static final List<String> LOCAL = Arrays.asList(null,"Applies","Needs adaptation","Not applicable");
    static final int[] STATE_LABELS = {R.string.b2_draft,R.string.b2_published,R.string.b2_retired};
    static final int[] LANG_LABELS = {R.string.kb_french,R.string.kb_msa,R.string.kb_darija};
    static final int[] EVIDENCE_LABELS = {R.string.kb_unspecified,R.string.kb_high,R.string.kb_moderate,R.string.kb_low,R.string.kb_expert};
    static final int[] LOCAL_LABELS = {R.string.kb_unspecified,R.string.b2_applies,R.string.b2_adapt,R.string.b2_na};
    String id;
    ReviewApi api;

    void setup(int layout) {
        layout(layout);
        id = getIntent().getStringExtra(ID);
        findViewById(R.id.b2_back).setOnClickListener(v -> finish());
    }

    boolean editingRole() { return staffRole() == null || "Content team".equals(staffRole()); }
    boolean clinicalRole() { return staffRole() == null || "Clinical reviewer".equals(staffRole()); }
    @Override protected boolean revokeOnForbidden() { return false; }

    @Override protected final void reload() {
        if (!authorized()) return;
        if (getIntent().hasExtra(SESSION) && !generation().equals(getIntent().getStringExtra(SESSION))) {
            clear(); finish(); return;
        }
        api = api(ReviewApi.class);
        note(0);
        loadPage();
    }
    abstract void loadPage();

    void open(Class<? extends Activity> target,String itemId) {
        if (!authorized()) return;
        startActivity(new Intent(this,target).putExtra(ID,itemId).putExtra(SESSION,generation()));
    }
    void loadError(int code) {
        clear();
        findViewById(R.id.b3_reload).setEnabled(authorized());
        note(code == 403 ? R.string.b2_no_access : code == 404 ? R.string.b2_missing : R.string.b2_network);
    }
    void show(int view,boolean visible) { findViewById(view).setVisibility(visible ? View.VISIBLE : View.GONE); }
    void text(int field,String value) { ((TextView)findViewById(field)).setText(value); }
    String optional(String v) { return v == null ? getString(R.string.kb_unspecified) : v; }
    String label(String value,List<String> codes,int[] labels) {
        int index = codes.indexOf(value);
        return index < 0 ? optional(value) : getString(labels[index]);
    }
    String meta(ReviewApi.Summary row) {
        return getString(R.string.b2_meta,row.contentId,row.contentVersion,label(row.approvalStatus,STATES,STATE_LABELS));
    }
    void choices(int field,int[] labels) {
        List<String> options = new ArrayList<>();
        for (int res : labels) options.add(getString(res));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner)findViewById(field)).setAdapter(adapter);
    }
    String input(int field) {
        String s = ((EditText)findViewById(field)).getText().toString();
        return s.trim().isEmpty() ? null : s;
    }
    boolean validDate(int field,String value) {
        if (value == null) return true;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
        f.setLenient(false);
        if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && f.parse(value,new ParsePosition(0)) != null) return true;
        ((EditText)findViewById(field)).setError(getString(R.string.b2_date_error));
        return false;
    }
}
