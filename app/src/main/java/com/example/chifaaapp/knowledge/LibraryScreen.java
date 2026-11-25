package com.example.chifaaapp.knowledge;

import android.content.Context;
import android.net.*;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.chifaaapp.R;
import java.io.IOException;
import java.util.concurrent.Future;

abstract class LibraryScreen extends AppCompatActivity {
    private ConnectivityManager connectivity;
    private Network lastNetwork;
    private boolean started;
    private int generation;
    private Future<?> pending;
    private final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable nextDay = new Runnable() {
        @Override public void run() {
            if (started) {
                reload(false);
                scheduleExpiryCheck();
            }
        }
    };
    private final Runnable sessionChanged = () -> { if (started) reload(true); };
    private final ConnectivityManager.NetworkCallback networks = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { runOnUiThread(() -> networkChanged()); }
        @Override public void onLost(Network network) { runOnUiThread(() -> networkChanged()); }
    };

    abstract void reload(boolean sync);
    String displayedId() { return null; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    void layout(int resource) {
        setContentView(resource);
        View root = findViewById(R.id.kb_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return insets;
        });
    }

    private void networkChanged() {
        if (!started) return;
        Network active = connectivity.getActiveNetwork();
        if (java.util.Objects.equals(lastNetwork, active)) return;
        lastNetwork = active;
        reload(active != null);
    }

    private void scheduleExpiryCheck() {
        long day = java.util.concurrent.TimeUnit.DAYS.toMillis(1);
        clock.postDelayed(nextDay, day - System.currentTimeMillis() % day);
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        KnowledgeSession.observers.add(sessionChanged);
        lastNetwork = connectivity.getActiveNetwork();
        connectivity.registerDefaultNetworkCallback(networks);
        scheduleExpiryCheck();
        reload(true);
    }

    @Override protected void onStop() {
        started = false;
        clock.removeCallbacks(nextDay);
        generation++;
        if (pending != null) pending.cancel(false);
        KnowledgeSession.observers.remove(sessionChanged);
        connectivity.unregisterNetworkCallback(networks);
        super.onStop();
    }

    interface Work<T> { T run(KnowledgeRepository repo, boolean online) throws IOException; }
    interface Display<T> { void show(T result); }

    <T> void load(Work<T> work, Display<T> display, boolean sync) {
        final int request = ++generation;
        if (pending != null) pending.cancel(false);
        KnowledgeRepository repo = KnowledgeSession.current;
        if (repo == null || repo.revoked) { error(401); return; }
        final boolean online = lastNetwork != null;
        status(R.string.kb_loading);
        pending = KnowledgeRepository.IO.submit(() -> {
            try {
                T result = work.run(repo, online);
                runOnUiThread(() -> {
                    if (started && generation == request && KnowledgeSession.current == repo && !repo.revoked)
                        display.show(result);
                });
                if (sync && online && !repo.revoked) {
                    repo.refreshVisited(displayedId()).whenComplete((nothing, failure) -> {
                        if (repo.revoked) runOnUiThread(() -> {
                            if (KnowledgeSession.current == repo) KnowledgeSession.close();
                        });
                    });
                }
            } catch (IOException | RuntimeException e) {
                int code = e instanceof KnowledgeRepository.Failure ? ((KnowledgeRepository.Failure)e).code : -1;
                runOnUiThread(() -> {
                    if (code == 401 && KnowledgeSession.current == repo) KnowledgeSession.close();
                    if (started && generation == request) error(code);
                });
            }
        });
    }

    void error(int code) {
        status(code == 401 ? R.string.kb_auth : code == 404 || code == 410 ? R.string.kb_hidden :
                code == 0 ? R.string.kb_not_saved : R.string.kb_error);
    }

    void status(int message) {
        TextView label = findViewById(R.id.kb_status);
        label.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) label.setText(message);
    }
}
