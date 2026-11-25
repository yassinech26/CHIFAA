package com.example.chifaaapp;

import android.os.Bundle;
import android.view.View;
import com.example.chifaaapp.safety.SafetyLauncher;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        findViewById(R.id.open_safety).setOnClickListener(v -> SafetyLauncher.open(this));
        findViewById(R.id.open_chat).setOnClickListener(v ->
                com.example.chifaaapp.knowledge.LibraryLauncher.open(this, com.example.chifaaapp.chat.ChatHistoryActivity.class));
        findViewById(R.id.open_library).setOnClickListener(v ->
                com.example.chifaaapp.knowledge.LibraryLauncher.open(this));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override protected void onResume() {
        super.onResume();
        findViewById(R.id.open_safety).setVisibility(SafetyLauncher.available() ? View.VISIBLE : View.GONE);
    }
}
