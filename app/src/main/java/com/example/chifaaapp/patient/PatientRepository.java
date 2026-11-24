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
        if (!BuildConfig.PATIENT_API.startsWith("https://")) throw new IllegalStateException("HTTPS required");
        var client=new OkHttpClient.Builder().followRedirects(false).addInterceptor(chain->{
            var builder=chain.request().newBuilder();
            String current=token;
            if (current!=null) builder.header("Authorization","Bearer "+current);
            return chain.proceed(builder.build());
        }).build();
        api=new Retrofit.Builder().baseUrl(BuildConfig.PATIENT_API).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(PatientApi.class);
    }
    void authenticate(String value) { token=value; }
    boolean signedIn() { return token!=null; }
}
