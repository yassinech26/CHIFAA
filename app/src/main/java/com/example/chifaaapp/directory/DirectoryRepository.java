package com.example.chifaaapp.directory;

import android.content.Context;
import androidx.room.*;
import com.example.chifaaapp.local.ApiRepository;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import retrofit2.HttpException;

public final class DirectoryRepository {
    @Database(entities=OrganisationCacheEntity.class, version=1, exportSchema=false)
    public abstract static class Cache extends RoomDatabase { public abstract OrganisationDao organisations(); }

    public static final class Result {
        public final List<JsonObject> items;
        public final long synchronizedAt;
        public final boolean offline;

        Result(List<JsonObject> items, long synchronizedAt, boolean offline) {
            this.items = items;
            this.synchronizedAt = synchronizedAt;
            this.offline = offline;
        }
    }

    private static Cache database;
    private final OrganisationDao dao;
    private final DirectoryApiService api;
    static synchronized Cache cache(Context context) {
        if (database == null) database = Room.databaseBuilder(context.getApplicationContext(),Cache.class,"public-directory").build();
        return database;
    }
    public DirectoryRepository(Context context) { this(cache(context).organisations(),ApiRepository.API); }

    DirectoryRepository(OrganisationDao dao,DirectoryApiService api) {
        this.dao = dao;
        this.api = api;
    }

    public Result sync() throws IOException {
        var items = new ArrayList<JsonObject>();
        try {
            int page = 0;
            JsonObject response;
            do {
                response = ApiRepository.execute(api.directory(page++));
                for (var item : response.getAsJsonArray("items")) items.add(item.getAsJsonObject());
            } while (response.get("hasNext").getAsBoolean());
        } catch (IOException | HttpException failure) {
            if (failure instanceof HttpException && ((HttpException)failure).code() < 500) throw failure;
            var cached = dao.all();
            if (cached.isEmpty()) throw failure;
            items.clear();
            for (var item : cached) items.add(JsonParser.parseString(item.json).getAsJsonObject());
            return new Result(items,cached.get(0).synchronizedAt,true);
        }
        long now = System.currentTimeMillis();
        var rows = new ArrayList<OrganisationCacheEntity>();
        for (var item : items) {
            var row = new OrganisationCacheEntity();
            row.id = ApiRepository.value(item,"id");
            row.json = item.toString();
            row.synchronizedAt = now;
            rows.add(row);
        }
        dao.replace(rows);
        return new Result(items,now,false);
    }
}
