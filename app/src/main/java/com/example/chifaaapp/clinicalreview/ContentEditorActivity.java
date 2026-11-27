package com.example.chifaaapp.clinicalreview;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.chifaaapp.R;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

public class ContentEditorActivity extends ReviewScreen {
    private static final int[] FIELDS = {R.id.b2_title,R.id.b2_topic,R.id.b2_body,R.id.b2_source,R.id.b2_reference,R.id.b2_expiry};
    private static final String[] KEYS = {"title","topic","contentText","sourceType","sourceReference","expiryDate"};
    private EditState state;
    private JsonObject original;
    private boolean form;

    /** Le texte éditorial survit à la rotation uniquement en mémoire et pour la même session du personnel. */
    public static class EditState extends ViewModel {
        String generation;
        JsonObject draft;
        boolean unconfirmed;
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setup(R.layout.b2_editor);
        state = new ViewModelProvider(this).get(EditState.class);
        choices(R.id.b2_language,LANG_LABELS); choices(R.id.b2_evidence,EVIDENCE_LABELS); choices(R.id.b2_local,LOCAL_LABELS);
        findViewById(R.id.b2_save).setOnClickListener(v -> save());
        findViewById(R.id.b2_recover).setOnClickListener(v -> {
            if (id != null) open(ContentVersionHistoryActivity.class,id);
            finish();
        });
    }
    @Override protected void onStop() {
        if (form && authorized()) state.draft = values();
        super.onStop();
    }
    @Override protected void clear() {
        form = false; original = null;
        show(R.id.b2_scroll,false); show(R.id.b2_recover,false);
        for (int field : FIELDS) text(field,"");
        findViewById(R.id.b3_reload).setEnabled(false);
    }

    @Override void loadPage() {
        clear();
        if (!generation().equals(state.generation)) {
            state.draft = null; state.unconfirmed = false; state.generation = generation();
        }
        if (!editingRole()) { note(R.string.b2_no_access); return; }
        if (state.unconfirmed) { recovery(R.string.b2_lost); return; }
        if (id == null) { render(null); return; }
        findViewById(R.id.b3_reload).setEnabled(true);
        fetch(api.get(id),item -> {
            if (!id.equals(item.contentId)) { loadError(0); return; }
            if (!"Draft".equals(item.approvalStatus) && !"Clinically approved".equals(item.approvalStatus)) {
                recovery(R.string.b2_conflict); return;
            }
            render(item);
        },this::loadError);
    }

    private void render(ReviewApi.Item item) {
        text(R.id.b2_meta,item == null ? "" : meta(item)); show(R.id.b2_meta,item != null);
        show(R.id.b2_notice,item == null || item.fictional);
        if (item != null) {
            String[] data = {item.title,item.topic,item.contentText,item.sourceType,item.sourceReference,item.expiryDate};
            for (int i=0;i<FIELDS.length;i++) text(FIELDS[i],data[i]);
        }
        select(R.id.b2_language,LANG,item == null ? "French" : item.language);
        select(R.id.b2_evidence,EVIDENCE,item == null ? null : item.evidenceLevel);
        select(R.id.b2_local,LOCAL,item == null ? null : item.localApplicability);
        original = values();
        if (state.draft != null) {
            for (int i=0;i<FIELDS.length;i++) text(FIELDS[i],value(state.draft,KEYS[i]));
            select(R.id.b2_language,LANG,value(state.draft,"language"));
            select(R.id.b2_evidence,EVIDENCE,value(state.draft,"evidenceLevel"));
            select(R.id.b2_local,LOCAL,value(state.draft,"localApplicability"));
        }
        form = true; show(R.id.b2_scroll,true);
        findViewById(R.id.b3_reload).setEnabled(false);
    }

    private void select(int field,List<String> codes,String code) {
        ((Spinner)findViewById(field)).setSelection(Math.max(0,codes.indexOf(code)));
    }
    private String selected(int field,List<String> codes) {
        return codes.get(((Spinner)findViewById(field)).getSelectedItemPosition());
    }
    private static String value(JsonObject obj,String key) {
        JsonElement entry = obj.get(key);
        return entry == null || entry.isJsonNull() ? null : entry.getAsString();
    }
    private JsonObject values() {
        JsonObject body = new JsonObject();
        for (int i=0;i<FIELDS.length;i++) body.addProperty(KEYS[i],input(FIELDS[i]));
        body.addProperty("language",selected(R.id.b2_language,LANG));
        body.addProperty("evidenceLevel",selected(R.id.b2_evidence,EVIDENCE));
        body.addProperty("localApplicability",selected(R.id.b2_local,LOCAL));
        return body;
    }

    private void save() {
        if (!form || !authorized()) return;
        for (int field : FIELDS) {
            if (field != R.id.b2_expiry && input(field) == null) {
                EditText edit = findViewById(field);
                edit.requestFocus(); edit.setError(getString(R.string.b2_required)); return;
            }
        }
        if (!validDate(R.id.b2_expiry,input(R.id.b2_expiry))) return;
        state.draft = values();
        JsonObject changes = new JsonObject();
        state.draft.entrySet().forEach(entry -> {
            if (id == null || !entry.getValue().equals(original.get(entry.getKey()))) changes.add(entry.getKey(),entry.getValue());
        });
        if (changes.size() == 0) { note(R.string.b2_same); return; }
        state.unconfirmed = true; form = false; show(R.id.b2_scroll,false); note(0);
        fetch(id == null ? api.create(changes) : api.patch(id,changes),item -> {
            if (item.contentId == null || !item.contentId.matches("KB-[0-9]{4}") || !"Draft".equals(item.approvalStatus)) {
                recovery(R.string.b2_lost); return;
            }
            state.unconfirmed = false; state.draft = null;
            open(ClinicalReviewActivity.class,item.contentId); finish();
        },code -> {
            if (code == 400) {
                state.unconfirmed = false; form = true; show(R.id.b2_scroll,true); note(R.string.b2_invalid);
            } else if (code == 403 || code == 404 || code == 409) {
                state.draft = null; state.unconfirmed = false;
                recovery(code == 403 ? R.string.b2_no_access : code == 404 ? R.string.b2_missing : R.string.b2_conflict);
            } else recovery(R.string.b2_lost);
        });
    }

    private void recovery(int message) {
        clear();
        text(R.id.b2_recover,getString(id == null ? R.string.b2_back : R.string.b2_history));
        show(R.id.b2_recover,true);
        note(message);
    }
}
