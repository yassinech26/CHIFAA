package com.example.chifaaapp.knowledge;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.chifaaapp.R;
import com.example.chifaaapp.MainActivity;
import com.google.gson.Gson;
import java.io.File;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.junit.*;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.anything;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibraryFlowTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String account = UUID.randomUUID().toString();
    private final Gson json = new Gson();
    private volatile boolean offline;
    private volatile int detailStatus = 200;
    private volatile HttpUrl lastList;
    private volatile String title = "Atlas fictif";
    private final KnowledgeFilter all = new KnowledgeFilter("", "", "");
    private KnowledgeRepository repo;

    @Before public void connect() { connect(account); }

    private void connect(String user) {
        OkHttpClient.Builder transport = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (offline) throw new UnknownHostException();
            Request request = chain.request();
            HttpUrl url = request.url();
            int code = 200;
            Object response;
            if (!"Bearer test-only".equals(request.header("Authorization"))) {
                code = 401;
                response = "";
            } else if (url.encodedPath().equals("/api/kb")) {
                lastList = url;
                KnowledgeApi.Page page = new KnowledgeApi.Page();
                if ("KB-0001".equals(url.queryParameter("after"))) {
                    page.items = Collections.singletonList(item("KB-0002", "Autre"));
                } else {
                    page.items = Collections.singletonList(item("KB-0001", title));
                    page.nextAfter = "KB-0001";
                }
                response = page;
            } else if (url.encodedPath().equals("/api/kb/KB-0001")) {
                code = detailStatus;
                response = item("KB-0001", title);
            } else {
                code = 404;
                response = "";
            }
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                    .message("Test").body(ResponseBody.create(MediaType.get("application/json"), json.toJson(response))).build();
        });
        KnowledgeSession.open(context, "https://m4.invalid/", user, "test-only", transport);
        repo = KnowledgeSession.current;
    }

    private KnowledgeApi.Item item(String id, String name) {
        KnowledgeApi.Item item = new KnowledgeApi.Item();
        item.contentId = id;
        item.title = name;
        item.topic = "Accueil";
        item.language = "French";
        item.contentText = "Texte fictif 100%_اختبار";
        item.sourceType = "Test";
        item.sourceReference = "Source de test";
        item.contentVersion = "v1.0";
        item.reviewDate = "2020-01-01";
        item.evidenceLevel = "Low";
        item.fictional = true;
        return item;
    }

    private void idle() throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        KnowledgeRepository.IO.submit(() -> {}).get(25, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @After public void logout() throws Exception {
        KnowledgeSession.close();
        idle();
    }

    @Test public void filtersPagesAndOpensThenReadsOfflineInUi() throws Exception {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.open_library)).perform(click());
            idle();
            onView(withId(R.id.kb_keyword)).perform(replaceText("Atlas"), closeSoftKeyboard());
            onView(withId(R.id.kb_topic)).perform(replaceText("Accueil"), closeSoftKeyboard());
            onView(withId(R.id.kb_language)).perform(click());
            onData(anything()).atPosition(1).perform(click());
            onView(withId(R.id.kb_apply)).perform(scrollTo(), click());
            idle();
            assertEquals("Atlas", lastList.queryParameter("q"));
            assertEquals("Accueil", lastList.queryParameter("topic"));
            assertEquals("French", lastList.queryParameter("language"));
            onView(withId(R.id.kb_more)).perform(scrollTo(), click());
            idle();
            assertEquals("KB-0001", lastList.queryParameter("after"));
            onView(withText("Atlas fictif")).perform(scrollTo(), click());
            idle();
            onView(withId(R.id.kb_body)).check(matches(withText("Texte fictif 100%_اختبار")));
            onView(withId(R.id.kb_notice)).check(matches(isDisplayed()));
            offline = true;
            pressBack();
            idle();
            onView(withId(R.id.kb_status)).check(matches(withText(R.string.kb_offline)));
            onView(withText("Atlas fictif")).perform(scrollTo(), click());
            idle();
            onView(withId(R.id.kb_status)).check(matches(withText(R.string.kb_cached_detail)));
            onView(withId(R.id.kb_body)).check(matches(withText("Texte fictif 100%_اختبار")));
            pressBack();
            idle();
            pressBack();
        }
    }

    @Test public void cachesOnlyVisitedAndRevalidatesOnConnection() throws Exception {
        repo.list(all, null, true);
        assertTrue(repo.list(all, null, false).value.items.isEmpty());
        assertFalse(repo.detail("KB-0001", true).cached);
        assertTrue(repo.detail("KB-0001", false).cached);
        assertEquals(1, repo.list(new KnowledgeFilter("%_اختبار", "accueil", "French"), null, false).value.items.size());
        assertTrue(repo.list(new KnowledgeFilter("", "", "Darija"), null, false).value.items.isEmpty());
        title = "Version relue";
        repo.refreshVisited(null).get(10, TimeUnit.SECONDS);
        assertEquals(title, repo.detail("KB-0001", false).value.title);
        detailStatus = 404;
        repo.refreshVisited(null).get(10, TimeUnit.SECONDS);
        assertTrue(repo.list(all, null, false).value.items.isEmpty());
    }

    @Test public void expirySurvivesReopeningCacheAndDenialPreventsFallback() throws Exception {
        File path = new File(context.getCacheDir(), UUID.randomUUID().toString());
        VisitedCache stored = new VisitedCache(path);
        try {
            KnowledgeApi.Item expired = item("KB-0001", title);
            expired.expiryDate = VisitedCache.today();
            stored.put(expired);
            assertNull(new VisitedCache(path).get("KB-0001"));
            assertTrue(stored.ids().isEmpty());
        } finally { stored.clear(); }
        repo.detail("KB-0001", true);
        detailStatus = 403;
        try { repo.detail("KB-0001", true); fail(); }
        catch (KnowledgeRepository.Failure denied) { assertEquals(403, denied.code); }
        assertFalse(repo.revoked);
        try { repo.detail("KB-0001", false); fail(); }
        catch (KnowledgeRepository.Failure missing) { assertEquals(0, missing.code); }
        detailStatus = 401;
        try { repo.detail("KB-0001", true); fail(); }
        catch (KnowledgeRepository.Failure denied) { assertEquals(401, denied.code); }
        assertTrue(repo.revoked);
        try { repo.detail("KB-0001", false); fail(); }
        catch (KnowledgeRepository.Failure denied) { assertEquals(401, denied.code); }
    }

    @Test public void switchingAccountsIsolatesVisitedHistory() throws Exception {
        repo.detail("KB-0001", true);
        connect(account); // Session restaurée sur le même compte.
        assertEquals(title, repo.detail("KB-0001", false).value.title);
        connect(UUID.randomUUID().toString());
        idle();
        assertTrue(repo.list(all, null, false).value.items.isEmpty());
        connect(account);
        idle();
        assertTrue(repo.list(all, null, false).value.items.isEmpty());
    }

    @Test public void serverFailureIsDistinctFromOfflineReading() throws Exception {
        repo.detail("KB-0001", true);
        detailStatus = 503;
        try { repo.detail("KB-0001", true); fail(); }
        catch (KnowledgeRepository.Failure unavailable) { assertEquals(503, unavailable.code); }
        offline = true;
        assertTrue(repo.detail("KB-0001", true).cached);
    }

    @Test public void arabicResourcesUseRtlAndIdentifyFictionalContent() {
        android.content.res.Configuration config =
                new android.content.res.Configuration(context.getResources().getConfiguration());
        config.setLocale(new java.util.Locale("ar"));
        Context arabic = context.createConfigurationContext(config);
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, arabic.getResources().getConfiguration().getLayoutDirection());
        assertEquals("محتوى افتراضي – غير معتمد", arabic.getString(R.string.kb_notice));
        assertEquals("الدارجة", arabic.getResources().getStringArray(R.array.kb_languages)[3]);
    }
}
