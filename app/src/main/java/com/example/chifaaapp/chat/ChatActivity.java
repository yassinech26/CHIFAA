package com.example.chifaaapp.chat;

import static com.example.chifaaapp.local.ApiRepository.value;
import android.os.Bundle;
import android.text.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.example.chifaaapp.R;
import com.example.chifaaapp.directory.DirectoryActivity;
import com.example.chifaaapp.databinding.*;
import com.example.chifaaapp.local.LocalActivity;
import com.google.android.material.button.MaterialButton;
import com.google.gson.*;
import java.util.*;

public class ChatActivity extends LocalActivity {
    private final ChatRepository repository = new ChatRepository();
    private ChatViewModel chat;
    private EditText draft;
    private MaterialButton submit, retry, older;

    private static final class History {
        final JsonArray messages;
        final int page;
        History(JsonArray messages, int page) { this.messages = messages; this.page = page; }
    }

    @Override protected int title() { return R.string.e3_chat; }

    @Override protected Class<? extends com.example.chifaaapp.local.ScreenViewModel> viewModelType() { return ChatViewModel.class; }

    @Override protected void setup(Bundle state) {
        chat = (ChatViewModel) model;
        if (!chat.initialized) chat.session = state == null ? id() : state.getString("session");
        text(ui.header,getString(R.string.e3_disclaimer),false);
        button(ui.header,R.string.e3_history,() -> open(ChatHistoryActivity.class));
        button(ui.header,R.string.e3_new_chat,() -> {
            if (chat.pendingId != null) return;
            model.run(repository::create);
        });
        older = button(ui.header,R.string.e3_older,() -> load(chat.page + 1));
        older.setVisibility(android.view.View.GONE);
        var composer = LayoutChatInputBinding.inflate(getLayoutInflater(),ui.footer,true);
        draft = composer.draft;
        submit = composer.send;
        retry = composer.retry;
        submit.setOnClickListener(v -> send());
        retry.setOnClickListener(v -> send());
        ui.toolbar.getMenu().add(R.string.e3_suggestions).setOnMenuItemClickListener(item -> {
            if (chat.pendingId != null || Boolean.TRUE.equals(model.busy().getValue())) return true;
            String[] suggestions = getResources().getStringArray(R.array.e3_chat_suggestions);
            new AlertDialog.Builder(this).setTitle(R.string.e3_suggestions)
                    .setItems(suggestions,(dialog, which) -> {
                        draft.setText(suggestions[which]);
                        send();
                    }).show();
            return true;
        });
        draft.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { updateActions(); }
            public void afterTextChanged(Editable s) {}
        });
        if (!chat.initialized && chat.session != null) load(0);
        chat.initialized = true;
        if (chat.pendingText != null) draft.setText(chat.pendingText);
        older.setVisibility(chat.hasOlder ? android.view.View.VISIBLE : android.view.View.GONE);
        retry.setVisibility(chat.failed ? android.view.View.VISIBLE : android.view.View.GONE);
        if (chat.failed) notice(getString(R.string.e3_not_sent));
        render(true);
        model.busy().observe(this,busy -> updateActions());
        updateActions();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("session",chat.session);
    }

    private void load(int pageToLoad) {
        String id = chat.session;
        if (id != null) model.run(() -> new History(repository.messages(id,pageToLoad),pageToLoad));
    }

    private void updateActions() {
        boolean busy = Boolean.TRUE.equals(model.busy().getValue());
        submit.setEnabled(!busy && chat.pendingId == null && !draft.getText().toString().trim().isEmpty());
        draft.setEnabled(chat.pendingId == null);
        retry.setEnabled(!busy);
    }

    private void send() {
        if (Boolean.TRUE.equals(model.busy().getValue())) return;
        if (chat.pendingId == null) {
            chat.pendingText = draft.getText().toString().trim();
            if (chat.pendingText.isEmpty()) return;
            chat.pendingId = UUID.randomUUID().toString();
        }
        String requestId = chat.pendingId, message = chat.pendingText, current = chat.session;
        chat.sending = true;
        chat.failed = false;
        model.run(() -> {
            String resolved = current == null ? repository.create() : current;
            try {
                var response = repository.send(resolved,requestId,message);
                response.addProperty("resolvedSession",resolved);
                return response;
            } catch (Exception failure) {
                return new FailedSend(resolved,failure);
            }
        });
        updateActions();
    }

    private static final class FailedSend {
        final String session;
        final Exception failure;
        FailedSend(String session, Exception failure) { this.session = session; this.failure = failure; }
    }

    @Override protected void onResult(Object result) {
        if (result instanceof FailedSend) {
            var failed = (FailedSend)result;
            chat.session = failed.session;
            onFailure(failed.failure);
            return;
        }
        notice("");
        if (result instanceof String) {
            chat.session = (String)result;
            chat.page = 0;
            chat.entries.clear();
            chat.hasOlder = false;
            older.setVisibility(android.view.View.GONE);
        } else if (result instanceof History) {
            var history = (History)result;
            chat.page = history.page;
            if (chat.page == 0) chat.entries.clear();
            int position = 0;
            for (var entry : history.messages) {
                var object = entry.getAsJsonObject();
                String messageId = value(object.getAsJsonObject("message"),"id");
                if (chat.entries.stream().noneMatch(existing -> messageId.equals(value(existing.getAsJsonObject("message"),"id")))) {
                    chat.entries.add(position++,object);
                }
            }
            chat.hasOlder = history.messages.size() == 100;
            older.setVisibility(chat.hasOlder ? android.view.View.VISIBLE : android.view.View.GONE);
        } else if (result instanceof JsonObject) {
            var sent = (JsonObject)result;
            chat.session = value(sent,"resolvedSession");
            var user = new JsonObject();
            user.add("message",sent.get("userMessage"));
            chat.entries.add(user);
            chat.entries.add(sent.getAsJsonObject("assistant"));
            chat.sending = false;
            chat.failed = false;
            chat.pendingText = null;
            chat.pendingId = null;
            retry.setVisibility(android.view.View.GONE);
            draft.setText("");
            updateActions();
        } else if (result instanceof Runnable) {
            ((Runnable)result).run();
        }
        render(!(result instanceof History) || ((History)result).page == 0);
    }

    @Override protected void onFailure(Exception failure) {
        super.onFailure(failure);
        if (chat.sending) {
            chat.sending = false;
            chat.failed = true;
            ui.status.append("\n" + getString(R.string.e3_not_sent));
            retry.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void render(boolean goToLatest) {
        var rows = new ArrayList<java.util.function.Consumer<LinearLayout>>();
        for (var item : chat.entries) rows.add(card -> renderMessage(card,item));
        show(rows);
        if (goToLatest && !rows.isEmpty()) ui.list.scrollToPosition(rows.size()-1);
    }

    private void renderMessage(LinearLayout card, JsonObject entry) {
        var message = entry.getAsJsonObject("message");
        boolean user = "User".equals(value(message,"senderRole"));
        var parent = (com.google.android.material.card.MaterialCardView)card.getParent();
        parent.setCardBackgroundColor(color(user ? R.color.primary : R.color.surface));
        parent.setStrokeWidth(user ? 0 : 1);
        parent.setStrokeColor(color(R.color.text_secondary));
        var date = value(message,"messageTimestamp");
        try { date = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, screenLocale())
                .format(Date.from(java.time.Instant.parse(date))); }
        catch (java.time.format.DateTimeParseException ignored) {}
        if (user) {
            var bubble = ItemMessageUserBinding.inflate(getLayoutInflater(),card,true);
            bubble.messageBody.setText(value(message,"messageText"));
            bubble.messageTime.setText(date);
            return;
        }
        var bubble = ItemMessageAssistantBinding.inflate(getLayoutInflater(),card,true);
        bubble.messageBody.setText(value(message,"messageText"));
        bubble.messageTime.setText(date);
        if ("true".equals(value(message,"distressFlag"))) {
            var support = LayoutSafetyResourcesBinding.inflate(getLayoutInflater(),card,true);
            if (entry.has("resources") && entry.get("resources").isJsonArray()) {
                for (var item : entry.getAsJsonArray("resources")) {
                    var resource = item.getAsJsonObject();
                    var url = android.net.Uri.parse(value(resource,"url"));
                    if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null) continue;
                    var link = button(support.supportLinks,R.string.e3_support,() -> {
                        try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,url)); }
                        catch (android.content.ActivityNotFoundException failure) { notice(getString(R.string.e3_error)); }
                    });
                    link.setText(value(resource,"name"));
                }
            }
        }
        if ("true".equals(value(entry,"showDirectory"))) button(card,R.string.e3_directory,() -> open(DirectoryActivity.class));
        if (entry.has("reference") && entry.get("reference").isJsonObject()) {
            var reference = entry.getAsJsonObject("reference");
            var citation = ItemKnowledgeReferenceBinding.inflate(getLayoutInflater(),card,true);
            citation.referenceTitle.setText(value(reference,"title"));
            citation.referenceSource.setText(value(reference,"source"));
            citation.referenceReview.setText(value(reference,"reviewedDate"));
            citation.fictional.setVisibility("true".equals(value(reference,"fictional")) ? android.view.View.VISIBLE : android.view.View.GONE);
            citation.openReference.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(value(reference,"title"))
                    .setMessage(value(reference,"body")).setPositiveButton(android.R.string.ok,null).show());
        }
        if (!"AI".equals(value(message,"senderRole"))) return;
        var rating = new RatingBar(this);
        rating.setNumStars(5);
        rating.setStepSize(1);
        rating.setContentDescription(getString(R.string.e3_rating));
        String saved = value(message,"userFeedback");
        if (!saved.isEmpty()) rating.setRating(Integer.parseInt(saved));
        rating.setOnRatingBarChangeListener((bar,score,fromUser) -> {
            if (!fromUser) return;
            int selected = Math.max(1,(int)score);
            bar.setRating(saved.isEmpty() ? 0 : Integer.parseInt(saved));
            model.run(() -> {
                repository.rate(value(message,"id"),selected);
                return (Runnable)() -> message.addProperty("userFeedback",selected);
            });
        });
        card.addView(rating,new LinearLayout.LayoutParams(-2,-2));
    }
}
