package com.example.chifaaapp.knowledge;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chifaaapp.R;
import java.util.Collections;

public class LibraryActivity extends LibraryScreen {
    private EditText keyword, topic;
    private Spinner language;
    private KnowledgeAdapter adapter;
    private String next;
    private Button more;
    private static final String[] LANGUAGES = {"", "French", "MSA", "Darija"};
    private KnowledgeFilter applied = new KnowledgeFilter("", "", "");

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        layout(R.layout.kb_library);
        keyword = findViewById(R.id.kb_keyword);
        topic = findViewById(R.id.kb_topic);
        language = findViewById(R.id.kb_language);
        more = findViewById(R.id.kb_more);
        if (state != null) applied = new KnowledgeFilter(state.getString("q", ""), state.getString("topic", ""),
                state.getString("language", ""));
        findViewById(R.id.kb_apply).setOnClickListener(v -> {
            applied = new KnowledgeFilter(keyword.getText().toString(), topic.getText().toString(),
                    LANGUAGES[language.getSelectedItemPosition()]);
            reload(false);
        });
        more.setOnClickListener(v -> page(next, false));
        adapter = new KnowledgeAdapter(id -> startActivity(new Intent(this, KnowledgeDetailActivity.class)
                .putExtra(KnowledgeDetailActivity.ID, id)));
        RecyclerView list = findViewById(R.id.kb_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("q", applied.keyword);
        state.putString("topic", applied.topic);
        state.putString("language", applied.language);
    }

    @Override void reload(boolean sync) {
        adapter.set(Collections.emptyList(), false);
        page(null, sync);
    }

    private void page(String after, boolean sync) {
        KnowledgeFilter filter = applied;
        more.setVisibility(View.GONE);
        load((repo, online) -> repo.list(filter, after, online), result -> {
            adapter.set(result.value.items, after != null && !result.cached);
            next = result.value.nextAfter;
            more.setVisibility(next == null ? View.GONE : View.VISIBLE);
            status(result.cached ? (adapter.getItemCount() == 0 ? R.string.kb_offline_empty : R.string.kb_offline)
                    : adapter.getItemCount() == 0 ? R.string.kb_empty : 0);
        }, sync);
    }

    @Override void error(int code) {
        if (code == 401 || code == 403) adapter.set(Collections.emptyList(), false);
        super.error(code);
    }
}
