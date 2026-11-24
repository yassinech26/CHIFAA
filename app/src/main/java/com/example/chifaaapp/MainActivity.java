package com.example.chifaaapp;

import static com.example.chifaaapp.local.ApiRepository.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.example.chifaaapp.admin.directory.AdminDirectoryActivity;
import com.example.chifaaapp.admin.safety.SafetyQueueActivity;
import com.example.chifaaapp.chat.*;
import com.example.chifaaapp.directory.DirectoryActivity;
import com.example.chifaaapp.local.*;
import com.google.gson.JsonObject;

public class MainActivity extends LocalActivity {
    private static final class Dashboard {
        final String role;
        final JsonObject safety, directory;
        Dashboard(String role, JsonObject safety, JsonObject directory) {
            this.role = role;
            this.safety = safety;
            this.directory = directory;
        }
    }
    private Dashboard dashboard;
    @Override protected int title() { return R.string.e3_home; }
    @Override protected void setup(Bundle state) {
        render();
        button(ui.footer,R.string.e3_language,this::chooseLanguage);
        if (state == null && BuildConfig.DEBUG && android.os.Build.VERSION.SDK_INT >= 37) {
            String host = java.net.URI.create(BuildConfig.API_URL).getHost();
            String permission = "android.permission.ACCESS_LOCAL_NETWORK";
            if (!"127.0.0.1".equals(host) && !"localhost".equals(host)
                    && checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{permission},37);
            }
        }
    }
    @Override protected void onResume() { super.onResume(); render(); loadDashboard(); }

    private void chooseLanguage() {
        new AlertDialog.Builder(this).setTitle(R.string.e3_language)
            .setItems(new String[]{"Français", "العربية"}, (dialog, which) ->
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(which == 0 ? "fr" : "ar")))
            .show();
    }

    private void loadDashboard() {
        String role = RetrofitClient.role();
        if (role == null || role.equals("USER")) return;
        model.run(() -> new Dashboard(role,
            role.equals("ADMIN") || role.equals("SAFETY_REVIEWER") ? execute(API.safetyStats()) : null,
            role.equals("ADMIN") || role.equals("CONTENT_TEAM") ? execute(API.directoryStats()) : null));
    }

    private void render() {
        String role = RetrofitClient.role();
        ui.toolbar.setTitle(role != null && !role.equals("USER") ? R.string.e3_demo_admin : R.string.e3_home);
        var cards = new java.util.ArrayList<java.util.function.Consumer<LinearLayout>>();
        cards.add(card -> {
            var heading = text(card,getString(role != null && !role.equals("USER") ? R.string.e3_staff : R.string.e3_welcome),true);
            heading.setTextSize(24);
            heading.setTextColor(color(R.color.primary));
            if (role == null || role.equals("USER")) text(card,getString(R.string.e3_intro),false);
        });
        if (role == null || role.equals("USER")) cards.add(card -> {
            button(card,R.string.e3_directory,() -> open(DirectoryActivity.class));
        });
        if (role == null || role.equals("USER")) cards.add(card -> {
            button(card,R.string.e3_chat,() -> {
                if (role == null) login(false); else open(ChatActivity.class);
            });
            if (role != null) button(card,R.string.e3_history,() -> open(ChatHistoryActivity.class));
        });
        if (dashboard != null && dashboard.role.equals(role)) cards.add(card -> {
            text(card,getString(R.string.e3_overview),true);
            metric(card,dashboard.safety,"open",R.string.e3_stat_open);
            metric(card,dashboard.safety,"critical",R.string.e3_stat_critical);
            metric(card,dashboard.safety,"resolved",R.string.e3_stat_resolved);
            metric(card,dashboard.directory,"organisations",R.string.e3_stat_organisations);
            metric(card,dashboard.directory,"due",R.string.e3_stat_due);
        });
        cards.add(card -> {
            if (role == null) {
                button(card,R.string.e3_account,() -> login(false));
                button(card,R.string.e3_staff_login,() -> login(true));
                return;
            }
            if (role.equals("SAFETY_REVIEWER") || role.equals("ADMIN")) {
                button(card,R.string.e3_queue,() -> open(SafetyQueueActivity.class));
            }
            if (role.equals("CONTENT_TEAM") || role.equals("ADMIN")) {
                button(card,R.string.e3_manage,() -> open(AdminDirectoryActivity.class));
            }
            button(card,R.string.e3_logout,() -> model.run(() -> {
                try { execute(API.logout()); }
                finally { RetrofitClient.clear(); }
                return null;
            }));
        });
        show(cards);
    }

    private void metric(LinearLayout card, JsonObject data, String key, int label) {
        if (data != null) text(card,getString(label,data.get(key).getAsLong()),false);
    }

    private void login(boolean staff) {
        var form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dim(R.dimen.e3_padding);
        form.setPadding(padding,0,padding,0);
        var username = input(form,R.string.e3_username,InputType.TYPE_CLASS_TEXT,"");
        var password = input(form,R.string.e3_password,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,"");
        var dialog = new AlertDialog.Builder(this)
            .setTitle(staff ? R.string.e3_staff_login : R.string.e3_account).setView(form)
            .setNegativeButton(R.string.e3_cancel,null).setPositiveButton(R.string.e3_login,null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String user=username.getText().toString().trim();
            String secret=password.getText().toString();
            if (user.isEmpty() || secret.isEmpty()) { password.setError(getString(R.string.e3_input_required)); return; }
            var credentials = new JsonObject();
            credentials.addProperty("username",user);
            credentials.addProperty("password",secret);
            credentials.addProperty("staff",staff);
            password.setText("");
            dialog.dismiss();
            model.run(() -> execute(API.login(credentials)));
        }));
        dialog.show();
    }

    @Override protected void onResult(Object result) {
        if (result instanceof Dashboard) dashboard = (Dashboard)result;
        if (result instanceof JsonObject) {
            var account = (JsonObject)result;
            RetrofitClient.save(value(account,"token"),value(account,"role"));
            loadDashboard();
        }
        notice("");
        render();
    }

    @Override protected void onFailure(Exception error) {
        super.onFailure(error);
        render();
    }
}
