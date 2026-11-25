package com.example.chifaaapp.safety;

import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.chifaaapp.R;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

abstract class SafetyScreen extends AppCompatActivity {
    SafetySession.Connection session;
    private Call<?> active;
    private boolean started;
    private final Runnable changed = () -> {
        if (started && session != SafetySession.current) bind();
    };

    void layout(int resource) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(resource);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.b3_root), (view, state) -> {
            Insets i = state.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(i.left, i.top, i.right, i.bottom);
            return state;
        });
        findViewById(R.id.b3_reload).setOnClickListener(v -> { if (authorized()) reload(); });
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        SafetySession.observers.add(changed);
        bind();
    }

    private void bind() {
        cancel();
        clear();
        session = SafetySession.current;
        if (session == null) note(R.string.b3_auth);
        else reload();
    }

    @Override protected void onStop() {
        started = false;
        SafetySession.observers.remove(changed);
        cancel();
        clear();
        super.onStop();
    }

    boolean authorized() { return started && session != null && session == SafetySession.current; }
    abstract void clear();
    abstract void reload();

    void note(int id) {
        TextView message = findViewById(R.id.b3_notice);
        message.setText(id == 0 ? "" : getString(id));
        message.setVisibility(id == 0 ? View.GONE : View.VISIBLE);
    }

    private void cancel() {
        if (active != null) active.cancel();
        active = null;
        findViewById(R.id.b3_progress).setVisibility(View.GONE);
    }

    interface Success<T> { void accept(T value); }
    interface Failure { void accept(int code); }

    <T> void fetch(Call<T> call, Success<T> success, Failure failure) {
        if (!authorized()) return;
        cancel();
        active = call;
        findViewById(R.id.b3_progress).setVisibility(View.VISIBLE);
        call.enqueue(new Callback<T>() {
            private boolean complete() {
                if (!authorized() || active != call) return false;
                active = null;
                findViewById(R.id.b3_progress).setVisibility(View.GONE);
                return true;
            }
            @Override public void onResponse(Call<T> ignored, Response<T> response) {
                if (!complete()) return;
                int code = response.code();
                if (code == 401 || code == 403) {
                    SafetySession.Connection denied = session;
                    session = null;
                    clear();
                    SafetySession.revoke(denied);
                    note(code == 401 ? R.string.b3_auth : R.string.b3_forbidden);
                } else if (response.isSuccessful() && response.body() != null) success.accept(response.body());
                else failure.accept(code);
            }
            @Override public void onFailure(Call<T> ignored, Throwable error) {
                if (complete()) failure.accept(0);
            }
        });
    }
}
