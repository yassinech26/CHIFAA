package com.example.chifaaapp.patient;

import com.example.chifaaapp.BuildConfig;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

final class PatientRepository {
    static final PatientRepository INSTANCE=new PatientRepository();
    private volatile String token;
    PatientApi api;
    private PatientRepository() {
        String endpoint = BuildConfig.PATIENT_API;
        boolean localDebug = BuildConfig.DEBUG && endpoint.matches("http://localhost:[0-9]{1,5}/api/");
        if (!endpoint.startsWith("https://") && !localDebug) {
            throw new IllegalStateException("HTTPS required");
        }
        var client=new OkHttpClient.Builder().followRedirects(false).addInterceptor(chain->{
            var builder=chain.request().newBuilder();
            String current=token;
            if (current!=null) builder.header("Authorization","Bearer "+current);
            return chain.proceed(builder.build());
        }).build();
        api=new Retrofit.Builder().baseUrl(endpoint).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(PatientApi.class);
    }
    void authenticate(String value) { token=value; }
    boolean signedIn() { return token!=null; }
}
