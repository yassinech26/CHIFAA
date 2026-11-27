package com.example.chifaaapp.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.chifaaapp.R;
import com.example.chifaaapp.knowledge.KnowledgeDetailActivity;
import com.example.chifaaapp.knowledge.KnowledgeSession;
import java.text.NumberFormat;
import java.util.UUID;
import retrofit2.Retrofit;

public class ChatActivity extends ChatScreen {
    private static final String SESSION = "chat.session", CLOSED = "chat.closed";
    private UUID session;
    private Draft draft;
    private EditText input;
    private ListView list;
    private MessageAdapter rows;
    private AlertDialog ratingDialog;
    private boolean editing, ready, more, closed, loading;
    private int requestedOffset;
    private enum Operation { HISTORY, SEND, RATE }
    private Operation operation = Operation.HISTORY;

    /** En mémoire uniquement. La rotation conserve aussi l’identifiant du message non confirmé. */
    public static class Draft extends ViewModel {
        Retrofit owner;
        String text = "";
        ChatRepository.Question pending;
        int offset;
        void reset() { owner = null; text = ""; pending = null; offset = 0; }
    }

    static Intent intent(Context context, ChatRepository.Session item) {
        return new Intent(context, ChatActivity.class).putExtra(SESSION, item.sessionId.toString())
                .putExtra(CLOSED, item.endedAt != null);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        layout(R.layout.chat_conversation);
        try { session = UUID.fromString(getIntent().getStringExtra(SESSION)); }
        catch (IllegalArgumentException | NullPointerException invalid) { finish(); }
        closed = getIntent().getBooleanExtra(CLOSED, false);
        draft = new ViewModelProvider(this).get(Draft.class);
        input = findViewById(R.id.chat_input);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (editing) { draft.text = s.toString(); controls(loading); }
            }
        });
        list = findViewById(R.id.chat_list);
        rows = new MessageAdapter(this);
        list.setAdapter(rows);
        findViewById(R.id.chat_send).setOnClickListener(v -> send());
        findViewById(R.id.chat_retry).setOnClickListener(v -> {
            if (operation == Operation.SEND && draft.pending != null) send();
            else history(requestedOffset);
        });
        findViewById(R.id.chat_previous).setOnClickListener(v -> history(draft.offset - ChatRepository.PAGE));
        findViewById(R.id.chat_next).setOnClickListener(v -> history(draft.offset + ChatRepository.PAGE));
    }

    boolean hasCurrentSession() {
        return api != null && client == KnowledgeSession.authenticatedClient();
    }

    @Override void connected() {
        if (session == null) return;
        if (draft.owner != null && draft.owner != client) {
            draft.reset();
            finish();
            return;
        }
        draft.owner = client;
        input.setText(draft.text);
        input.setSelection(input.length());
        editing = true;
        history(draft.offset);
    }

    private void history(int offset) {
        requestedOffset = offset;
        operation = Operation.HISTORY;
        retry(false);
        request(api.history(session, offset, ChatRepository.PAGE + 1), page -> {
            draft.offset = offset;
            more = page.size() > ChatRepository.PAGE;
            rows.replace(page.subList(0, Math.min(ChatRepository.PAGE, page.size())));
            if (draft.pending != null) {
                for (ChatRepository.Message item : page) {
                    if (draft.pending.messageId.equals(item.messageId)) { confirmed(); break; }
                }
            }
            ready = true;
            if (draft.pending != null && !more) {
                operation = Operation.SEND;
                status(R.string.chat_pending);
                retry(true);
            } else status(closed ? R.string.chat_closed : page.isEmpty() ? R.string.chat_first : 0);
            controls(false);
            if (!more) list.setSelection(rows.getCount() - 1);
        });
    }

    private void send() {
        if (!hasCurrentSession() || !ready || closed || more || loading) return;
        if (draft.pending == null) {
            String text = draft.text.trim();
            if (text.isEmpty()) return;
            draft.pending = new ChatRepository.Question(text);
        }
        operation = Operation.SEND;
        retry(false);
        request(api.send(session, draft.pending), turn -> {
            if (turn.question == null || turn.answer == null || !draft.pending.messageId.equals(turn.question.messageId)
                    || turn.answer.messageId == null || !session.equals(turn.answer.sessionId)) {
                failed(0); return;
            }
            rows.append(turn.question);
            rows.append(turn.answer);
            confirmed();
            if (rows.getCount() > ChatRepository.PAGE) {
                history(draft.offset + ChatRepository.PAGE);
                return;
            }
            controls(false);
            list.setSelection(rows.getCount() - 1);
        });
    }

    private void confirmed() {
        draft.pending = null;
        draft.text = "";
        input.setText("");
    }

    void openReference(String id) {
        if (hasCurrentSession()) startActivity(new Intent(this, KnowledgeDetailActivity.class)
                .putExtra(KnowledgeDetailActivity.ID, id));
    }

    void chooseRating(ChatRepository.Message message) {
        if (!hasCurrentSession() || loading) return;
        String[] choices = new String[5];
        for (int i = 0; i < choices.length; i++) choices[i] = NumberFormat.getIntegerInstance().format(i + 1);
        ratingDialog = new AlertDialog.Builder(this).setTitle(R.string.chat_rate)
                .setItems(choices, (dialog, choice) -> {
                    if (!hasCurrentSession()) return;
                    int rating = choice + 1;
                    operation = Operation.RATE;
                    retry(false);
                    request(api.feedback(message.messageId, new ChatRepository.Feedback(rating)), unused -> {
                        message.userFeedback = rating;
                        rows.notifyDataSetChanged();
                        if (draft.pending != null) { operation = Operation.SEND; status(R.string.chat_pending); retry(true); }
                    });
                }).create();
        ratingDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ratingDialog.show();
    }

    private void retry(boolean show) {
        findViewById(R.id.chat_retry).setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override void clearScreen() {
        if (ratingDialog != null) { ratingDialog.dismiss(); ratingDialog = null; }
        editing = false;
        input.setText("");
        rows.clear();
        ready = false;
        more = false;
        status(0);
        retry(false);
        controls(false);
    }

    @Override void controls(boolean busy) {
        loading = busy;
        boolean available = api != null && ready && !busy;
        boolean compose = available && !closed && !more && draft.pending == null;
        input.setEnabled(compose);
        findViewById(R.id.chat_send).setEnabled(compose && !draft.text.trim().isEmpty());
        pageControl(R.id.chat_previous, ready && draft.offset > 0, busy);
        pageControl(R.id.chat_next, ready && more, busy);
        rows.interactive(available && draft.pending == null);
    }

    @Override void failed(int code) {
        if (code == 401 || code == 403 || (code == 404 && operation != Operation.RATE)) {
            clearScreen();
            draft.reset();
            status(errorLabel(code));
        } else if (operation == Operation.SEND && (code == 400 || code == 409)) {
            draft.pending = null;
            if (code == 409) closed = true;
            status(R.string.chat_invalid);
        } else if (operation == Operation.SEND) {
            status(code == 503 ? R.string.chat_no_reply : R.string.chat_pending);
            retry(true);
        } else if (operation == Operation.RATE) {
            status(R.string.chat_feedback_error);
        } else {
            status(errorLabel(code));
            retry(true);
        }
        controls(false);
    }
}
