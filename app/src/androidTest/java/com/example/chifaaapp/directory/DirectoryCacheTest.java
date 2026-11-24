package com.example.chifaaapp.directory;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DirectoryCacheTest {
    @Test public void preservesLastCompleteSnapshotWhenNextPageFails() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var cache = Room.inMemoryDatabaseBuilder(context,DirectoryRepository.Cache.class).build();
        try {
            var calls = new AtomicInteger();
            var http = new OkHttpClient.Builder().addInterceptor(chain -> {
                int call = calls.getAndIncrement();
                if (call >= 2) throw new IOException("Simulated loss of connectivity");
                String id = call == 0 ? "ORG-001" : "ORG-002";
                var body = "{\"items\":[{\"id\":\"" + id + "\"}],\"hasNext\":" + (call != 0) + "}";
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(body,MediaType.get("application/json"))).build();
            }).build();
            var api = new Retrofit.Builder().baseUrl("https://localhost/").client(http)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(DirectoryApiService.class);
            var repository = new DirectoryRepository(cache.organisations(),api);
            var online = repository.sync();
            assertFalse(online.offline);
            var offline = repository.sync();
            assertTrue(offline.offline);
            assertEquals(online.synchronizedAt,offline.synchronizedAt);
            assertEquals(1,offline.items.size());
            assertEquals("ORG-001",offline.items.get(0).get("id").getAsString());
        } finally { cache.close(); }
    }
}
