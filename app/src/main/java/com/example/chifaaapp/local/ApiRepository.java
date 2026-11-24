package com.example.chifaaapp.local;

import com.google.gson.*;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.HttpException;

public final class ApiRepository {
    public static final ChifaaApi API = RetrofitClient.API;

    private ApiRepository() {}
    public static <T> T execute(Call<T> call) throws IOException {
        var response = call.execute();
        if (!response.isSuccessful()) throw new HttpException(response);
        return response.body();
    }

    public static String value(JsonObject item, String key) {
        var value = item.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
