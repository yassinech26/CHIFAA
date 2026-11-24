package com.example.chifaaapp.knowledge;

import android.app.Activity;
import android.content.Intent;

public final class LibraryLauncher {
    private LibraryLauncher() {}
    public static void open(Activity from) { from.startActivity(new Intent(from, LibraryActivity.class)); }
}
