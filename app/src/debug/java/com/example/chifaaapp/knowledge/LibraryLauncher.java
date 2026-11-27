package com.example.chifaaapp.knowledge;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import com.example.chifaaapp.R;

/** Permet de vérifier le vrai backend avant la livraison de la connexion mobile d'E1. */
public final class LibraryLauncher {
    private LibraryLauncher() {}
    public static void open(Activity from) {
        open(from, LibraryActivity.class);
    }
    public static void open(Activity from, Class<? extends Activity> destination) {
        if (KnowledgeSession.current != null && !KnowledgeSession.current.revoked) {
            from.startActivity(new Intent(from, destination));
            return;
        }
        View form = from.getLayoutInflater().inflate(R.layout.kb_debug_connect, null);
        AlertDialog dialog = new AlertDialog.Builder(from).setTitle(R.string.kb_dev_connect).setView(form)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnShowListener(event -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String token = ((EditText)form.findViewById(R.id.kb_dev_token)).getText().toString().trim();
            EditText url = form.findViewById(R.id.kb_dev_url);
            String base = url.getText().toString().trim();
            try {
                KnowledgeSession.open(from, base.endsWith("/") ? base : base + "/", KnowledgeSession.digest(token), token);
                dialog.dismiss();
                from.startActivity(new Intent(from, destination));
            } catch (IllegalArgumentException e) { url.setError(from.getString(R.string.kb_dev_error)); }
        }));
        dialog.show();
    }
}
