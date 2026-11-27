package com.example.chifaaapp.chat;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.example.chifaaapp.R;
import com.example.chifaaapp.knowledge.KnowledgeApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class MessageAdapter extends BaseAdapter {
    private final ChatActivity screen;
    private final List<ChatRepository.Message> messages = new ArrayList<>();
    private final Map<String, Call<KnowledgeApi.Item>> loading = new HashMap<>();
    private final Map<String, KnowledgeApi.Item> references = new HashMap<>();
    private final Set<String> retired = new HashSet<>();
    private boolean interactive;

    MessageAdapter(ChatActivity screen) { this.screen = screen; }

    void replace(List<ChatRepository.Message> page) {
        clear();
        messages.addAll(page);
        notifyDataSetChanged();
    }

    void append(ChatRepository.Message message) {
        for (ChatRepository.Message item : messages)
            if (item.messageId.equals(message.messageId)) return;
        messages.add(message);
        notifyDataSetChanged();
    }

    void clear() {
        for (Call<?> call : loading.values()) call.cancel();
        loading.clear();
        references.clear();
        retired.clear();
        messages.clear();
        notifyDataSetChanged();
    }

    void interactive(boolean value) {
        if (interactive == value) return;
        interactive = value;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return messages.size(); }
    @Override public ChatRepository.Message getItem(int position) { return messages.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public boolean areAllItemsEnabled() { return false; }
    @Override public boolean isEnabled(int position) { return false; }

    @Override public View getView(int position, View row, ViewGroup parent) {
        if (row == null) row = LayoutInflater.from(screen).inflate(R.layout.chat_message, parent, false);
        ChatRepository.Message message = getItem(position);
        boolean user = "User".equals(message.senderRole);
        boolean ai = "AI".equals(message.senderRole);
        boolean unavailable = "ReferenceUnavailable".equals(message.replyKind) || retired.contains(message.contentId);
        View bubble = row.findViewById(R.id.chat_bubble);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bubble.getLayoutParams();
        params.gravity = user ? Gravity.END : Gravity.START;
        int margin = screen.getResources().getDimensionPixelSize(R.dimen.kb_margin);
        params.setMarginStart(user ? margin : 0);
        params.setMarginEnd(user ? 0 : margin);
        bubble.setLayoutParams(params);
        bubble.setBackgroundResource(user ? R.drawable.chat_user : R.drawable.chat_companion);
        TextView role = row.findViewById(R.id.chat_role);
        role.setText(user ? R.string.chat_user : ai ? R.string.chat_ai : R.string.chat_reviewer);
        row.findViewById(R.id.chat_resource_heading).setVisibility(
                ai && "Distress".equals(message.replyKind) ? View.VISIBLE : View.GONE);
        TextView body = row.findViewById(R.id.chat_body);
        body.setText(unavailable ? screen.getString(R.string.chat_unavailable) : message.text);
        Button source = row.findViewById(R.id.chat_reference);
        boolean linked = !unavailable && ai && "Knowledge".equals(message.replyKind) && message.contentId != null;
        source.setVisibility(linked ? View.VISIBLE : View.GONE);
        if (linked) {
            String id = message.contentId;
            KnowledgeApi.Item ref = references.get(id);
            String label = ref == null || ref.title() == null ? id : ref.title();
            String caption = screen.getString(R.string.chat_ref, label);
            if (ref != null && ref.sourceReference() != null) caption += "\n" + ref.sourceReference();
            source.setText(caption);
            source.setEnabled(interactive);
            source.setOnClickListener(v -> screen.openReference(id));
            if (!references.containsKey(id) && !loading.containsKey(id)) fetch(id);
        }
        Button rate = row.findViewById(R.id.chat_rate);
        rate.setVisibility(ai ? View.VISIBLE : View.GONE);
        rate.setText(message.userFeedback == null ? screen.getString(R.string.chat_rate)
                : screen.getString(R.string.chat_rating, message.userFeedback));
        rate.setEnabled(interactive);
        rate.setOnClickListener(v -> screen.chooseRating(message));
        return row;
    }

    private void fetch(String id) {
        if (screen.api == null) return;
        Call<KnowledgeApi.Item> call = screen.client.create(KnowledgeApi.class).detail(id);
        loading.put(id, call);
        call.enqueue(new Callback<KnowledgeApi.Item>() {
            private boolean current() {
                return loading.get(id) == call && screen.hasCurrentSession();
            }
            @Override public void onResponse(Call<KnowledgeApi.Item> ignored, Response<KnowledgeApi.Item> response) {
                if (!current()) return;
                loading.remove(id);
                if (response.code() == 401 || response.code() == 403) { screen.denied(response.code()); return; }
                if (response.code() == 404) retired.add(id);
                else references.put(id, response.body());
                notifyDataSetChanged();
            }
            @Override public void onFailure(Call<KnowledgeApi.Item> ignored, Throwable failure) {
                if (!current()) return;
                loading.remove(id);
                references.put(id, null);
            }
        });
    }
}
