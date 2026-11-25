package com.example.chifaaapp.safety;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import com.example.chifaaapp.R;

public final class SafetyLauncher {
    private SafetyLauncher() {}
    public static boolean available() { return true; }
    public static void open(Activity from) {
        Intent queue = new Intent(from, SafetyQueueActivity.class);
        if (SafetySession.current != null) { from.startActivity(queue); return; }
        View form = from.getLayoutInflater().inflate(R.layout.kb_debug_connect, null);
        AlertDialog dialog = new AlertDialog.Builder(from).setTitle(R.string.b3_staff).setView(form)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnShowListener(event -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            EditText address = form.findViewById(R.id.kb_dev_url);
            EditText token = form.findViewById(R.id.kb_dev_token);
            String url = address.getText().toString().trim();
            try {
                SafetySession.open(url.endsWith("/") ? url : url + "/", token.getText().toString().trim());
                token.setText("");
                dialog.dismiss();
                from.startActivity(queue);
            } catch (IllegalArgumentException invalid) { address.setError(from.getString(R.string.kb_dev_error)); }
        }));
        dialog.show();
    }
}
