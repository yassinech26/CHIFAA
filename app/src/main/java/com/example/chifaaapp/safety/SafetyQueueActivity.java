package com.example.chifaaapp.safety;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.example.chifaaapp.R;
import java.util.ArrayList;
import java.util.List;

public class SafetyQueueActivity extends SafetyScreen {
    private final List<SafetyRepository.Event> page = new ArrayList<>();
    private SafetyLabels labels;
    private BaseAdapter rows;
    private int filter, offset;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        layout(R.layout.b3_queue);
        if (state != null) { filter = state.getInt("filter"); offset = state.getInt("offset"); }
        labels = new SafetyLabels(this);
        Spinner spinner = findViewById(R.id.b3_filter);
        labels.withEmpty(spinner, R.string.b3_all, R.array.b3_states, filter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == filter) return;
                filter = position;
                offset = 0;
                if (authorized()) reload();
            }
        });
        rows = new BaseAdapter() {
            public int getCount() { return page.size(); }
            public SafetyRepository.Event getItem(int index) { return page.get(index); }
            public long getItemId(int index) { return index; }
            public View getView(int index, View row, ViewGroup parent) {
                if (row == null) row = getLayoutInflater().inflate(R.layout.b3_row, parent, false);
                SafetyRepository.Event e = getItem(index);
                ((TextView)row.findViewById(R.id.b3_row_title)).setText(e.triggerType);
                ((TextView)row.findViewById(R.id.b3_row_meta)).setText(labels.meta(e));
                ((TextView)row.findViewById(R.id.b3_row_date)).setText(labels.date(e.flaggedAt));
                return row;
            }
        };
        ListView list = findViewById(R.id.b3_list);
        list.setAdapter(rows);
        list.setOnItemClickListener((parent, view, index, rowId) -> {
            if (!authorized()) return;
            Intent detail = new Intent(this, SafetyEventDetailsActivity.class);
            detail.putExtra("event",page.get(index).eventId.toString()).putExtra("generation",session.generation)
                    .putExtra("filter",status()).putExtra("offset",offset);
            startActivity(detail);
        });
        findViewById(R.id.b3_prev).setOnClickListener(v -> move(-SafetyRepository.PAGE));
        findViewById(R.id.b3_next).setOnClickListener(v -> move(SafetyRepository.PAGE));
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("filter", filter);
        out.putInt("offset", offset);
    }

    private String status() { return filter == 0 ? null : SafetyLabels.STATES.get(filter - 1); }
    private void move(int count) { if (authorized()) { offset += count; reload(); } }

    @Override void clear() {
        page.clear();
        rows.notifyDataSetChanged();
        findViewById(R.id.b3_prev).setVisibility(View.GONE);
        findViewById(R.id.b3_next).setVisibility(View.GONE);
        findViewById(R.id.b3_filter).setEnabled(false);
        findViewById(R.id.b3_reload).setEnabled(false);
        note(0);
    }

    @Override void reload() {
        clear();
        findViewById(R.id.b3_filter).setEnabled(true);
        findViewById(R.id.b3_reload).setEnabled(true);
        fetch(session.api.list(status(),offset,SafetyRepository.PAGE+1), items -> {
            if (items.isEmpty() && offset > 0) { offset = 0; reload(); return; }
            page.addAll(items.subList(0, Math.min(items.size(), SafetyRepository.PAGE)));
            rows.notifyDataSetChanged();
            findViewById(R.id.b3_prev).setVisibility(offset > 0 ? View.VISIBLE : View.GONE);
            findViewById(R.id.b3_next).setVisibility(items.size() > SafetyRepository.PAGE ? View.VISIBLE : View.GONE);
            if (page.isEmpty()) note(R.string.b3_empty);
        }, code -> note(R.string.b3_error));
    }
}
