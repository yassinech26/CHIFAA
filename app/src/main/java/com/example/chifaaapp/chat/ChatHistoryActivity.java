package com.example.chifaaapp.chat;

import static com.example.chifaaapp.local.ApiRepository.value;
import android.os.Bundle;
import android.widget.LinearLayout;
import com.example.chifaaapp.R;
import com.example.chifaaapp.databinding.ItemChatSessionBinding;
import com.example.chifaaapp.local.LocalActivity;
import com.google.gson.*;
import java.util.*;

public class ChatHistoryActivity extends LocalActivity {
    private final ChatRepository repository = new ChatRepository();
    private final List<JsonObject> items = new ArrayList<>();
    private int page;
    private com.google.android.material.button.MaterialButton more;

    @Override protected int title() { return R.string.e3_history; }
    @Override protected void setup(Bundle state) {
        button(ui.header,R.string.e3_new_chat,() -> open(ChatActivity.class));
        more = button(ui.footer,R.string.e3_older,this::load);
        load();
    }

    private void load() {
        int current = page;
        model.run(() -> repository.sessions(current));
    }

    @Override protected void onResult(Object result) {
        var list = (JsonArray)result;
        page++;
        more.setVisibility(list.size() == 50 ? android.view.View.VISIBLE : android.view.View.GONE);
        for (var item : list) items.add(item.getAsJsonObject());
        var rows = new ArrayList<java.util.function.Consumer<LinearLayout>>();
        for (var item : items) rows.add(card -> {
            String date = displayTimestamp(value(item,"startedAt"));
            var row = ItemChatSessionBinding.inflate(getLayoutInflater(),card,true);
            row.sessionDate.setText(date);
            row.sessionPreview.setText(value(item,"preview"));
            row.openSession.setOnClickListener(v -> open(ChatActivity.class,value(item,"id")));
        });
        show(rows);
        notice(items.isEmpty() ? getString(R.string.e3_empty) : "");
    }
}
