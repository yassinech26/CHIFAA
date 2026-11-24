package com.example.chifaaapp.chat;
import static com.example.chifaaapp.local.ApiRepository.*;
import com.google.gson.*;
import java.io.IOException;

public final class ChatRepository {
    private static final ChatApiService API = com.example.chifaaapp.local.RetrofitClient.create(ChatApiService.class);
    public String create() throws IOException { return value(execute(API.createSession()),"id"); }
    public JsonArray sessions(int page) throws IOException { return execute(API.sessions(page)); }
    public JsonArray messages(String id,int page) throws IOException { return execute(API.messages(id,page)); }
    public JsonObject send(String id,String request,String text) throws IOException {
        var body = new JsonObject();
        body.addProperty("requestId",request);
        body.addProperty("text",text);
        return execute(API.send(id,body));
    }
    public void rate(String id,int rating) throws IOException {
        var body = new JsonObject();
        body.addProperty("rating",rating);
        execute(API.rate(id,body));
    }
}
