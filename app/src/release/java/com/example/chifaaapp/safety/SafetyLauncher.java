package com.example.chifaaapp.safety;

import android.app.Activity;
import android.content.Intent;

public final class SafetyLauncher {
    private SafetyLauncher() {}
    public static boolean available() { return SafetySession.current != null; }
    public static void open(Activity from) {
        open(from,SafetyQueueActivity.class);
    }
    public static void open(Activity from, Class<? extends Activity> destination) {
        if (available()) from.startActivity(new Intent(from, destination));
    }
}
