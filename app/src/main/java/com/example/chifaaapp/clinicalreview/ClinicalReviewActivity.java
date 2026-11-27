package com.example.chifaaapp.clinicalreview;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;
import com.example.chifaaapp.R;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Objects;

public class ClinicalReviewActivity extends ReviewScreen {
    private ReviewApi.Item item;
    private AlertDialog dialog;
    private boolean refused;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setup(R.layout.b2_review);
        findViewById(R.id.b2_edit).setOnClickListener(v -> open(ContentEditorActivity.class,id));
        findViewById(R.id.b2_history).setOnClickListener(v -> open(ContentVersionHistoryActivity.class,id));
        findViewById(R.id.b2_approve).setOnClickListener(v -> confirm(true));
        findViewById(R.id.b2_retire).setOnClickListener(v -> confirm(false));
    }

    @Override protected void clear() {
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        item = null;
        show(R.id.b2_scroll,false);
        for (int field : new int[]{R.id.b2_title,R.id.b2_meta,R.id.b2_body,R.id.b2_details,R.id.b2_expiry}) text(field,"");
        findViewById(R.id.b3_reload).setEnabled(false);
    }

    @Override void loadPage() { read(0); }
    private void read(int message) {
        clear();
        findViewById(R.id.b3_reload).setEnabled(true);
        if (id == null) { loadError(404); return; }
        fetch(api.get(id),result -> {
            if (!id.equals(result.contentId)) { loadError(0); return; }
            display(result); note(message);
        },this::loadError);
    }

    private void display(ReviewApi.Item result) {
        item = result;
        text(R.id.b2_title,item.title); text(R.id.b2_meta,meta(item)); text(R.id.b2_body,item.contentText);
        text(R.id.b2_notice,item.fictional ? getString(R.string.kb_notice) : ""); show(R.id.b2_notice,item.fictional);
        text(R.id.b2_details,getString(R.string.b2_details,item.topic,label(item.language,LANG,LANG_LABELS),
                item.sourceType,item.sourceReference,label(item.evidenceLevel,EVIDENCE,EVIDENCE_LABELS),
                label(item.localApplicability,LOCAL,LOCAL_LABELS),optional(item.clinicalReviewer),
                optional(item.reviewDate),optional(item.expiryDate)));
        text(R.id.b2_expiry,item.expiryDate);
        boolean draft = "Draft".equals(item.approvalStatus);
        boolean live = draft || "Clinically approved".equals(item.approvalStatus);
        show(R.id.b2_edit,editingRole() && live);
        show(R.id.b2_approval,clinicalRole() && !refused && draft);
        show(R.id.b2_retire,clinicalRole() && !refused && live);
        show(R.id.b2_scroll,true);
        findViewById(R.id.b3_reload).setEnabled(true);
    }

    private void confirm(boolean approve) {
        if (!authorized() || item == null || refused) return;
        String expiry = input(R.id.b2_expiry);
        if (approve && !validDate(R.id.b2_expiry,expiry)) return;
        ReviewApi.Item reviewed = item;
        String prompt = approve ? getString(R.string.b2_confirm_approve,item.title,id,item.contentVersion,optional(expiry))
                : getString(R.string.b2_confirm_retire,item.title,id,item.contentVersion);
        dialog = new AlertDialog.Builder(this).setMessage(prompt).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(approve ? R.string.b2_approve : R.string.b2_retire,(d,w) -> {
                    if (authorized() && item == reviewed) decide(approve,expiry);
                }).create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }

    private void decide(boolean approve,String expiry) {
        JsonObject patch = new JsonObject();
        String decision = approve ? "Clinically approved" : "Retired";
        patch.addProperty("approvalStatus",decision);
        if (approve && !Objects.equals(expiry,item.expiryDate)) {
            if (expiry == null) patch.add("expiryDate",JsonNull.INSTANCE);
            else patch.addProperty("expiryDate",expiry);
        }
        show(R.id.b2_scroll,false);
        findViewById(R.id.b3_reload).setEnabled(false); note(0);
        fetch(api.patch(id,patch),updated -> {
            if (!id.equals(updated.contentId) || !decision.equals(updated.approvalStatus)) { read(R.string.b2_lost); return; }
            display(updated); note(R.string.b2_saved);
            ((ScrollView)findViewById(R.id.b2_scroll)).scrollTo(0,0);
        },code -> {
            if (code == 403) refused = true;
            if (code == 400) {
                display(item); text(R.id.b2_expiry,expiry); note(R.string.b2_invalid);
            } else if (code == 404) loadError(code);
            else read(code == 409 ? R.string.b2_conflict : code == 403 ? R.string.b2_no_access : R.string.b2_lost);
        });
    }
}
