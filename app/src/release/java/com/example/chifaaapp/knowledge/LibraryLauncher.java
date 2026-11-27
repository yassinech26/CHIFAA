package com.example.chifaaapp.knowledge;

import android.app.Activity;
import android.content.Intent;

public final class LibraryLauncher {
    private LibraryLauncher() {}
    public static void open(Activity from) { open(from, LibraryActivity.class); }
    public static void open(Activity from, Class<? extends Activity> destination) {
        from.startActivity(new Intent(from, destination));
    }
}
