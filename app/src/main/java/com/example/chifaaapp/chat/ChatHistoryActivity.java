package com.example.chifaaapp.chat;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.example.chifaaapp.R;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ChatHistoryActivity extends ChatScreen {
    private final List<ChatRepository.Session> sessions = new ArrayList<>();
    private ArrayAdapter<String> rows;
    private int offset;
    private int requestedOffset;
    private boolean more;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        layout(R.layout.chat_history);
        rows = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView list = findViewById(R.id.chat_list);
        list.setAdapter(rows);
        list.setOnItemClickListener((parent, view, index, id) -> open(sessions.get(index)));
        findViewById(R.id.chat_new).setOnClickListener(v -> request(api.create(), this::open));
        findViewById(R.id.chat_previous).setOnClickListener(v -> load(offset - ChatRepository.PAGE));
        findViewById(R.id.chat_next).setOnClickListener(v -> load(offset + ChatRepository.PAGE));
        findViewById(R.id.chat_retry).setOnClickListener(v -> load(requestedOffset));
    }

    private void open(ChatRepository.Session session) {
        if (session.sessionId != null) startActivity(ChatActivity.intent(this, session));
    }

    @Override void connected() { load(0); }

    private void load(int pageOffset) {
        requestedOffset = pageOffset;
        findViewById(R.id.chat_retry).setVisibility(View.GONE);
        request(api.sessions(pageOffset, ChatRepository.PAGE + 1), page -> {
            rows.clear();
            sessions.clear();
            offset = pageOffset;
            more = page.size() > ChatRepository.PAGE;
            sessions.addAll(page.subList(0, Math.min(page.size(), ChatRepository.PAGE)));
            for (ChatRepository.Session session : sessions) {
                String label = getString(R.string.chat_session, date(session.startedAt));
                if (session.endedAt != null) label += "\n" + getString(R.string.chat_closed);
                rows.add(label);
            }
            status(sessions.isEmpty() ? R.string.chat_empty : 0);
            controls(false);
        });
    }

    private String date(String timestamp) {
        if (timestamp == null) return "";
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        try { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(utc.parse(timestamp)); }
        catch (ParseException invalid) { return timestamp; }
    }

    @Override void controls(boolean loading) {
        findViewById(R.id.chat_new).setEnabled(api != null && !loading);
        findViewById(R.id.chat_list).setEnabled(!loading);
        pageControl(R.id.chat_previous, offset > 0, loading);
        pageControl(R.id.chat_next, more, loading);
    }

    @Override void clearScreen() {
        sessions.clear();
        rows.clear();
        offset = 0;
        more = false;
        status(0);
        findViewById(R.id.chat_retry).setVisibility(View.GONE);
        controls(false);
    }

    @Override void failed(int code) {
        status(errorLabel(code));
        findViewById(R.id.chat_retry).setVisibility(api == null ? View.GONE : View.VISIBLE);
        controls(false);
    }
}
