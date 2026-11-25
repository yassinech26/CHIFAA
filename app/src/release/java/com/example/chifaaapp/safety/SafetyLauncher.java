package com.example.chifaaapp.safety;

import android.app.Activity;
import android.content.Intent;

public final class SafetyLauncher {
    private SafetyLauncher() {}
    public static boolean available() { return SafetySession.current != null; }
    public static void open(Activity from) {
        if (available()) from.startActivity(new Intent(from, SafetyQueueActivity.class));
    }
}
