package com.example.chifaaapp.clinicalreview;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.example.chifaaapp.R;
import java.util.*;

public class ContentListActivity extends ReviewScreen {
    private static final int PAGE = 50;
    private final List<ReviewApi.Summary> rows = new ArrayList<>();
    private BaseAdapter adapter;
    private int offset,filter;
    boolean history() { return false; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setup(R.layout.b2_list);
        if (state != null) { filter = state.getInt("filter"); offset = state.getInt("offset"); }
        if (history()) text(R.id.b2_heading,getString(R.string.b2_history));
        show(R.id.b2_filters,!history());
        Spinner spinner = findViewById(R.id.b2_filter);
        choices(R.id.b2_filter,new int[]{R.string.b2_all,STATE_LABELS[0],STATE_LABELS[1],STATE_LABELS[2]});
        spinner.setSelection(filter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p,View view,int index,long row) {
                if (filter != index) { filter = index; offset = 0; reload(); }
            }
        });
        adapter = new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public ReviewApi.Summary getItem(int index) { return rows.get(index); }
            public long getItemId(int index) { return index; }
            public View getView(int index,View row,ViewGroup parent) {
                if (row == null) row = getLayoutInflater().inflate(R.layout.b3_row,parent,false);
                ReviewApi.Summary item = getItem(index);
                ((TextView)row.findViewById(R.id.b3_row_title)).setText(item.title);
                ((TextView)row.findViewById(R.id.b3_row_meta)).setText(meta(item));
                TextView notice = row.findViewById(R.id.b3_row_date);
                notice.setText(item.fictional ? getString(R.string.kb_notice) : "");
                notice.setVisibility(item.fictional ? View.VISIBLE : View.GONE);
                return row;
            }
        };
        ListView list = findViewById(R.id.b2_rows);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,i,l) -> open(ClinicalReviewActivity.class,rows.get(i).contentId));
        findViewById(R.id.b2_create).setOnClickListener(v -> open(ContentEditorActivity.class,null));
        findViewById(R.id.b2_prev).setOnClickListener(v -> { offset -= PAGE; reload(); });
        findViewById(R.id.b2_next).setOnClickListener(v -> { offset += PAGE; reload(); });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("filter",filter); state.putInt("offset",offset);
    }
    @Override protected void clear() {
        rows.clear(); adapter.notifyDataSetChanged();
        show(R.id.b2_create,false); show(R.id.b2_prev,false); show(R.id.b2_next,false);
        findViewById(R.id.b3_reload).setEnabled(false);
    }

    @Override void loadPage() {
        clear();
        findViewById(R.id.b3_reload).setEnabled(true);
        if (history() && id == null) { loadError(404); return; }
        String status = filter == 0 ? null : STATES.get(filter-1);
        fetch(history() ? api.history(id,offset,PAGE+1) : api.list(status,offset,PAGE+1), page -> {
            if (page.isEmpty() && offset > 0) { offset = 0; reload(); return; }
            rows.addAll(page.subList(0,Math.min(PAGE,page.size())));
            adapter.notifyDataSetChanged();
            show(R.id.b2_prev,offset>0); show(R.id.b2_next,page.size()>PAGE);
            show(R.id.b2_create,!history() && editingRole());
            if (page.isEmpty()) note(R.string.b2_empty);
        },this::loadError);
    }
}
