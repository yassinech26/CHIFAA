package com.example.chifaaapp.knowledge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** E1 appelle open après authentification/restauration de session, et close à la déconnexion. */
public final class KnowledgeSession {
    private KnowledgeSession() {}
    static volatile KnowledgeRepository current;
    private static String scope;
    static final Set<Runnable> observers = new CopyOnWriteArraySet<>();

    public static synchronized void open(Context context, String baseUrl, String userPid, String accessToken) {
        open(context, baseUrl, userPid, accessToken, new OkHttpClient.Builder());
    }

    static synchronized void open(Context context, String baseUrl, String userPid, String accessToken,
                                  OkHttpClient.Builder transport) {
        HttpUrl url = HttpUrl.parse(baseUrl);
        if (url == null || !url.isHttps() || !url.username().isEmpty() || !url.password().isEmpty()
                || url.query() != null || url.fragment() != null || !url.encodedPath().endsWith("/")
                || userPid == null || userPid.isEmpty() || accessToken == null || accessToken.trim().isEmpty())
            throw new IllegalArgumentException();
        String key = digest(url.toString() + "\n" + userPid);
        transport.interceptors().add(0, chain -> chain.proceed(chain.request().newBuilder()
                .header("Authorization", "Bearer " + accessToken).build()));
        OkHttpClient client = transport.followRedirects(false).build();
        KnowledgeApi api = new Retrofit.Builder().baseUrl(url).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(KnowledgeApi.class);
        File directory = new File(context.getNoBackupFilesDir(), "m4/" + key);
        replace(new KnowledgeRepository(api, new VisitedCache(directory)), key);
    }

    static synchronized void replace(KnowledgeRepository next, String key) {
        KnowledgeRepository old = current;
        if (old != null) {
            old.revoked = true;
            if (!key.equals(scope)) KnowledgeRepository.IO.execute(old::clear);
        }
        scope = key;
        current = next;
        Handler main = new Handler(Looper.getMainLooper());
        for (Runnable observer : observers) main.post(observer);
    }

    public static synchronized void close() { replace(null, ""); }

    static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte v : bytes) hex.append(String.format(java.util.Locale.ROOT, "%02x", v));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
