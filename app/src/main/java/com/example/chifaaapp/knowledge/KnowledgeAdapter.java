package com.example.chifaaapp.knowledge;

import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chifaaapp.R;
import java.util.ArrayList;
import java.util.List;

final class KnowledgeAdapter extends RecyclerView.Adapter<KnowledgeAdapter.Row> {
    interface Open { void item(String id); }
    private final List<KnowledgeApi.Item> rows = new ArrayList<>();
    private final Open open;
    KnowledgeAdapter(Open open) { this.open = open; }

    void set(List<KnowledgeApi.Item> items, boolean append) {
        if (!append) rows.clear();
        rows.addAll(items);
        notifyDataSetChanged();
    }

    static class Row extends RecyclerView.ViewHolder {
        final TextView title, meta, notice;
        Row(View v) {
            super(v);
            title = v.findViewById(R.id.row_title);
            meta = v.findViewById(R.id.row_meta);
            notice = v.findViewById(R.id.row_notice);
        }
    }

    @Override public Row onCreateViewHolder(ViewGroup parent, int type) {
        return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.kb_row, parent, false));
    }

    @Override public void onBindViewHolder(Row row, int index) {
        KnowledgeApi.Item item = rows.get(index);
        row.title.setText(item.title);
        row.meta.setText(row.itemView.getContext().getString(R.string.kb_row_meta, item.topic,
                row.itemView.getContext().getString(languageLabel(item.language))));
        row.notice.setVisibility(Boolean.TRUE.equals(item.fictional) ? View.VISIBLE : View.GONE);
        row.itemView.setOnClickListener(v -> open.item(item.contentId));
    }

    static int languageLabel(String language) {
        if ("MSA".equals(language)) return R.string.kb_msa;
        if ("Darija".equals(language)) return R.string.kb_darija;
        return R.string.kb_french;
    }

    @Override public int getItemCount() { return rows.size(); }
}
