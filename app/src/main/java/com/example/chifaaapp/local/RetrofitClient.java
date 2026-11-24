package com.example.chifaaapp.local;

import com.example.chifaaapp.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {
    private static volatile String token;
    private static volatile String role;
    private static final Retrofit RETROFIT = new Retrofit.Builder().baseUrl(BuildConfig.API_URL)
            .client(new OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).addInterceptor(chain -> {
                var request = chain.request();
                String current = token;
                if (current != null) request = request.newBuilder().header("Authorization", "Bearer " + current).build();
                return chain.proceed(request);
            }).build()).addConverterFactory(GsonConverterFactory.create()).build();
    public static final ChifaaApi API = create(ChifaaApi.class);

    private RetrofitClient() {}
    public static <T> T create(Class<T> service) { return RETROFIT.create(service); }
    public static String role() { return role; }
    public static void save(String accessToken, String newRole) { token = accessToken; role = newRole; }
    public static void clear() { role = null; token = null; }
}
