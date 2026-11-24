package com.example.chifaaapp.chat;

import com.example.chifaaapp.local.ScreenViewModel;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public final class ChatViewModel extends ScreenViewModel {
    final List<JsonObject> entries = new ArrayList<>();
    String session;
    String pendingText;
    String pendingId;
    int page;
    boolean hasOlder;
    boolean sending;
    boolean failed;
    boolean initialized;
}
