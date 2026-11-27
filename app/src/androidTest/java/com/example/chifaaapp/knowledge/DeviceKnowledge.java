package com.example.chifaaapp.knowledge;

import android.content.Context;
import com.example.chifaaapp.DeviceTls;
import com.google.gson.JsonObject;
import org.junit.Assert;

public final class DeviceKnowledge {
    private DeviceKnowledge() {}

    public static void open(Context context, JsonObject fixture, String account, String token) throws Exception {
        open(context, fixture, account, token, null);
    }

    public static void open(Context context, JsonObject fixture, String account, String token, Runnable onUnauthorized) throws Exception {
        KnowledgeSession.open(context, fixture.get("url").getAsString(), fixture.get(account).getAsString(),
                fixture.get(token).getAsString(), DeviceTls.client(fixture), onUnauthorized);
    }

    public static void read(String id, boolean online) throws Exception {
        KnowledgeRepository.IO.submit(() -> {
            KnowledgeRepository.Result<KnowledgeApi.Item> result = KnowledgeSession.current.detail(id, online);
            Assert.assertEquals(id, result.value.contentId);
            Assert.assertEquals(!online, result.cached);
            return null;
        }).get();
    }

    public static void assertListedOnline(String id) throws Exception {
        KnowledgeRepository.IO.submit(() -> {
            KnowledgeRepository.Result<KnowledgeApi.Page> result = KnowledgeSession.current.list(
                    new KnowledgeFilter("", "", ""), null, true);
            Assert.assertFalse(result.cached);
            Assert.assertTrue(result.value.items.stream().anyMatch(item -> id.equals(item.contentId)));
            return null;
        }).get();
    }

    public static void assertOfflineMissing(String id) throws Exception {
        KnowledgeRepository.IO.submit(() -> {
            try {
                KnowledgeSession.current.detail(id, false);
                Assert.fail("Fiche présente dans le cache du mauvais compte");
            } catch (KnowledgeRepository.Failure expected) {
                Assert.assertEquals(0, expected.code);
            }
            return null;
        }).get();
    }
}
