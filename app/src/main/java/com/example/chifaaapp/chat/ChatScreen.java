package com.example.chifaaapp.chat;

import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import com.example.chifaaapp.R;
import com.example.chifaaapp.knowledge.KnowledgeSession;
import retrofit2.*;

abstract class ChatScreen extends AppCompatActivity {
    Retrofit client;
    ChatRepository api;
    private Call<?> active;
    private boolean started;
    private final Runnable changed = () -> {
        if (started && client != KnowledgeSession.authenticatedClient()) bind();
    };

    void layout(int id) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(id);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chat_root), (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return insets;
        });
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        KnowledgeSession.observe(changed);
        bind();
    }

    private void bind() {
        cancel();
        clearScreen();
        client = KnowledgeSession.authenticatedClient();
        api = client == null ? null : client.create(ChatRepository.class);
        if (api == null) denied(401);
        else connected();
    }

    @Override protected void onStop() {
        started = false;
        KnowledgeSession.removeObserver(changed);
        cancel();
        clearScreen();
        super.onStop();
    }

    private void cancel() {
        if (active != null) active.cancel();
        active = null;
        busy(false);
    }

    void pageControl(int id, boolean visible, boolean loading) {
        View view = findViewById(id);
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
        view.setEnabled(!loading);
    }

    abstract void clearScreen();
    abstract void connected();
    abstract void controls(boolean loading);
    abstract void failed(int code);

    final void status(int id) {
        TextView v = findViewById(R.id.chat_status);
        v.setVisibility(id == 0 ? View.GONE : View.VISIBLE);
        if (id != 0) v.setText(id);
    }

    final void busy(boolean loading) {
        findViewById(R.id.chat_progress).setVisibility(loading ? View.VISIBLE : View.GONE);
        controls(loading);
    }

    final void denied(int code) {
        cancel();
        clearScreen();
        api = null;
        failed(code);
        if (code == 401 && client != null && client == KnowledgeSession.authenticatedClient()) KnowledgeSession.close();
    }

    interface Success<T> { void show(T value); }

    final <T> void request(Call<T> call, Success<T> success) {
        if (!started || api == null || client != KnowledgeSession.authenticatedClient() || active != null) return;
        active = call;
        busy(true);
        status(0);
        call.enqueue(new Callback<T>() {
            private boolean accept() {
                return started && active == call && client == KnowledgeSession.authenticatedClient();
            }
            @Override public void onResponse(Call<T> ignored, Response<T> response) {
                if (!accept()) return;
                active = null;
                busy(false);
                int code = response.code();
                if (code == 401 || code == 403) { denied(code); return; }
                if (!response.isSuccessful() || (response.body() == null && code != 204)) {
                    failed(code); return;
                }
                success.show(response.body());
            }
            @Override public void onFailure(Call<T> ignored, Throwable failure) {
                if (!accept()) return;
                active = null;
                busy(false);
                failed(0);
            }
        });
    }

    static int errorLabel(int code) {
        if (code == 401) return R.string.kb_auth;
        if (code == 403) return R.string.chat_consent;
        if (code == 404) return R.string.chat_missing;
        if (code == 0) return R.string.chat_network;
        return R.string.chat_error;
    }
}
