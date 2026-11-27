package com.example.chifaaapp.knowledge;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.example.chifaaapp.R;

public class KnowledgeDetailActivity extends LibraryScreen {
    public static final String ID = "knowledge.contentId";
    private String id;
    @Override String displayedId() { return id; }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        layout(R.layout.kb_detail);
        id = getIntent().getStringExtra(ID);
        findViewById(R.id.kb_retry).setOnClickListener(v -> reload(false));
        findViewById(R.id.kb_back).setOnClickListener(v -> finish());
    }

    @Override void reload(boolean sync) {
        findViewById(R.id.kb_content).setVisibility(View.GONE);
        if (id == null || !id.matches("KB-[0-9]{4}")) { error(404); return; }
        load((repo, online) -> repo.detail(id, online), result -> {
            KnowledgeApi.Item item = result.value;
            text(R.id.kb_title, item.title);
            text(R.id.kb_body, item.contentText);
            text(R.id.kb_metadata, getString(R.string.kb_metadata, item.topic,
                    getString(KnowledgeAdapter.languageLabel(item.language)), item.sourceType, item.sourceReference,
                    item.reviewDate, optional(item.expiryDate), evidence(item.evidenceLevel), item.contentVersion));
            findViewById(R.id.kb_notice).setVisibility(Boolean.TRUE.equals(item.fictional) ? View.VISIBLE : View.GONE);
            findViewById(R.id.kb_content).setVisibility(View.VISIBLE);
            status(result.cached ? R.string.kb_cached_detail : 0);
        }, sync);
    }

    private String optional(String value) { return value == null ? getString(R.string.kb_unspecified) : value; }
    private String evidence(String value) {
        if ("High".equals(value)) return getString(R.string.kb_high);
        if ("Moderate".equals(value)) return getString(R.string.kb_moderate);
        if ("Low".equals(value)) return getString(R.string.kb_low);
        if ("Expert opinion".equals(value)) return getString(R.string.kb_expert);
        return optional(value);
    }
    private void text(int id, String value) { ((TextView)findViewById(id)).setText(value); }
    @Override void error(int code) {
        findViewById(R.id.kb_content).setVisibility(View.GONE);
        super.error(code);
    }
}
