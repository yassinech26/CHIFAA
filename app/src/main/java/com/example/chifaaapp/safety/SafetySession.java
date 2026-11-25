package com.example.chifaaapp.safety;

import android.os.Handler;
import android.os.Looper;
import com.google.gson.GsonBuilder;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** B1 fournit et retire le jeton du personnel. Aucun jeton ni événement sur disque. */
public final class SafetySession {
    private SafetySession() {}
    static final class Connection {
        final String generation = UUID.randomUUID().toString();
        final SafetyRepository api;
        final Retrofit retrofit;
        final String role;
        Connection(Retrofit retrofit, String role) {
            this.retrofit = retrofit;
            this.api = retrofit.create(SafetyRepository.class);
            this.role = role;
        }
    }
    static volatile Connection current;
    static final Set<Runnable> observers = new CopyOnWriteArraySet<>();

    public static void open(String baseUrl, String staffToken) {
        open(baseUrl, staffToken, new OkHttpClient.Builder());
    }

    static void open(String baseUrl, String staffToken, OkHttpClient.Builder transport) {
        open(baseUrl,staffToken,null,transport);
    }

    /** Rôle du compte fourni par B1 pour adapter les commandes des écrans. Le serveur autorise chaque écriture. */
    public static void open(String baseUrl, String staffToken, String role) {
        open(baseUrl,staffToken,role,new OkHttpClient.Builder());
    }

    static void open(String baseUrl, String staffToken, String role, OkHttpClient.Builder transport) {
        HttpUrl url = baseUrl == null ? null : HttpUrl.parse(baseUrl);
        if (url == null || !url.isHttps() || !url.encodedPath().endsWith("/") || !url.username().isEmpty()
                || !url.password().isEmpty() || url.query() != null || url.fragment() != null
                || staffToken == null || !staffToken.matches("[!-~]+")) throw new IllegalArgumentException();
        transport.interceptors().add(0, chain -> chain.proceed(chain.request().newBuilder()
                .header("Authorization", "Bearer " + staffToken).build()));
        Retrofit retrofit = new Retrofit.Builder().baseUrl(url)
                .client(transport.followRedirects(false).retryOnConnectionFailure(false).build())
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().serializeNulls().create())).build();
        replace(new Connection(retrofit,role));
    }

    public static void close() { replace(null); }

    static synchronized void revoke(Connection denied) {
        if (current == denied) replace(null);
    }

    private static synchronized void replace(Connection next) {
        current = next;
        Handler main = new Handler(Looper.getMainLooper());
        for (Runnable observer : observers) main.post(observer);
    }
}
