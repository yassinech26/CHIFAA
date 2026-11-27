package com.example.chifaaapp.patient;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.example.chifaaapp.BuildConfig;
import com.example.chifaaapp.R;
import com.example.chifaaapp.knowledge.KnowledgeSession;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

final class PatientRepository {
    private static final String ENDPOINT = BuildConfig.PATIENT_API;
    static final PatientRepository INSTANCE=new PatientRepository();
    private volatile Models.Session session;
    private Retrofit knowledgeClient;
    PatientApi api;
    private PatientRepository() {
        var client=new OkHttpClient.Builder().followRedirects(false).addInterceptor(chain->{
            var builder=chain.request().newBuilder();
            Models.Session current=session;
            if (current!=null) builder.header("Authorization","Bearer "+current.token);
            return chain.proceed(builder.build());
        }).build();
        api=new Retrofit.Builder().baseUrl(ENDPOINT).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(PatientApi.class);
    }

    boolean authenticate(Models.Session value) {
        if (value == null || value.token == null || value.token.trim().isEmpty()
                || value.userPid == null || value.userPid.isEmpty()) return false;
        close();
        session = value;
        return true;
    }

    void restoreKnowledge(Context context) {
        Models.Session current = session;
        if (current == null || (knowledgeClient != null && knowledgeClient == KnowledgeSession.authenticatedClient())) return;
        Context app = context.getApplicationContext();
        String baseUrl = ENDPOINT.substring(0, ENDPOINT.length() - "api/".length());
        KnowledgeSession.open(app, baseUrl, current.userPid, current.token, () -> unauthorized(app, current));
        knowledgeClient = KnowledgeSession.authenticatedClient();
    }

    void close() {
        session = null;
        if (knowledgeClient != null) KnowledgeSession.close();
        knowledgeClient = null;
    }

    void unauthorized(Context context, Models.Session expected) {
        if (expected == null || session != expected) return;
        close();
        Toast.makeText(context, R.string.expired, Toast.LENGTH_LONG).show();
        context.startActivity(new Intent(context, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    Models.Session session() { return session; }
    boolean signedIn() { return session != null; }
}
