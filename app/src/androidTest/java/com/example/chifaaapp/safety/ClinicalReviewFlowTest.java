package com.example.chifaaapp.safety;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.chifaaapp.MainActivity;
import com.example.chifaaapp.R;
import com.example.chifaaapp.clinicalreview.ContentListActivity;
import com.google.gson.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import okhttp3.*;
import okio.Buffer;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ClinicalReviewFlowTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<JsonObject> items = new ArrayList<>();
    private final String root = "/api/admin/kb";
    private volatile String lastWrite, historyId, filter;
    private volatile int offset, writes, writeCode = 200, readCode = 200;
    private volatile JsonObject sent;
    private volatile boolean loseReply;

    @Before public void start() { login("Content team"); }
    @After public void end() { SafetySession.close(); }
    private void login(String role) {
        SafetySession.open("https://b2.invalid/","staff-fixture",role,
                new OkHttpClient.Builder().addInterceptor(chain -> serve(chain.request())));
    }

    private JsonObject create(String title) {
        JsonObject item = new JsonObject();
        item.addProperty("contentId",String.format(Locale.ROOT,"KB-%04d",items.size()+1));
        item.addProperty("contentVersion","v1."+items.size()); item.addProperty("approvalStatus","Draft");
        item.addProperty("title",title); item.addProperty("language","French"); item.addProperty("topic","Accueil");
        item.addProperty("sourceType","Test A"); item.addProperty("sourceReference","Source fictive");
        item.addProperty("contentText","Texte fictif"); item.addProperty("fictional",true);
        items.add(item); return item;
    }
    private String str(JsonObject item,String field) { return item.get(field).getAsString(); }

    private synchronized Response serve(Request req) throws IOException {
        String path = req.url().encodedPath();
        JsonElement body = JsonNull.INSTANCE;
        int code = 404;
        if (!"Bearer staff-fixture".equals(req.header("Authorization"))) code = 401;
        else if (req.method().equals("GET")) {
            code = readCode;
            if (path.equals(root) || path.endsWith("/versions")) {
                boolean history = path.endsWith("/versions");
                if (history) historyId = path.substring(root.length()+1,path.lastIndexOf('/'));
                filter = req.url().queryParameter("status");
                offset = Integer.parseInt(req.url().queryParameter("offset"));
                int limit = Integer.parseInt(req.url().queryParameter("limit"));
                JsonArray page = new JsonArray();
                int found = 0;
                for (JsonObject item : items) {
                    if (filter != null && !filter.equals(str(item,"approvalStatus"))) continue;
                    if (found++ >= offset && page.size() < limit) page.add(item);
                }
                body = page;
            } else {
                for (JsonObject item : items) if (path.equals(root+"/"+str(item,"contentId"))) body = item;
                if (body.isJsonNull() && code==200) code = 404;
            }
        } else if (req.method().equals("POST") || req.method().equals("PATCH")) {
            writes++; lastWrite = path;
            Buffer buffer = new Buffer(); req.body().writeTo(buffer);
            sent = JsonParser.parseString(buffer.readUtf8()).getAsJsonObject();
            code = writeCode;
            if (code == 200 && path.equals(root) && req.method().equals("POST")) {
                JsonObject draft = create("Initial"); sent.entrySet().forEach(e -> draft.add(e.getKey(),e.getValue()));
                body = draft; code = 201;
            } else if (code == 200 && req.method().equals("PATCH")) {
                JsonObject current = null;
                for (JsonObject item : items) if (path.equals(root+"/"+str(item,"contentId"))) current = item;
                if (current == null) code = 404;
                else if (sent.has("approvalStatus")) {
                    current.add("approvalStatus",sent.get("approvalStatus"));
                    if (sent.has("expiryDate")) current.add("expiryDate",sent.get("expiryDate"));
                    current.addProperty("clinicalReviewer","CR-FICTIF"); current.addProperty("reviewDate","2026-01-10");
                    body = current;
                } else {
                    JsonObject copy = current.deepCopy(); current.addProperty("approvalStatus","Retired");
                    JsonObject next = create("Next");
                    for (var e : copy.entrySet())
                        if (!e.getKey().equals("contentId") && !e.getKey().equals("contentVersion")) next.add(e.getKey(),e.getValue());
                    sent.entrySet().forEach(e -> next.add(e.getKey(),e.getValue()));
                    body = next;
                }
            }
            if (loseReply) { loseReply = false; throw new IOException("Fixture"); }
        }
        return new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                .body(ResponseBody.create(MediaType.get("application/json"),body.toString())).build();
    }

    private void visible(Matcher<View> matcher) {
        long deadline = SystemClock.elapsedRealtime()+12000;
        while (true) {
            try { onView(matcher).check(matches(isDisplayed())); return; }
            catch (AssertionError | NoMatchingViewException waiting) {
                if (SystemClock.elapsedRealtime() >= deadline) throw waiting;
                SystemClock.sleep(100);
            }
        }
    }
    private void enter(int field,String value) { onView(withId(field)).perform(scrollTo(),replaceText(value),closeSoftKeyboard()); }
    private void tap(int field) { onView(withId(field)).perform(scrollTo(),click()); }
    private void openRow(String title) {
        visible(withText(title)); onView(withText(title)).perform(click()); visible(withId(R.id.b2_body));
    }
    private void newForm() {
        visible(withId(R.id.b2_create)); onView(withId(R.id.b2_create)).perform(click());
        visible(withId(R.id.b2_title));
        enter(R.id.b2_title,"Article fictif"); enter(R.id.b2_topic,"Accueil"); enter(R.id.b2_body,"Corps de test");
        enter(R.id.b2_source,"Test A"); enter(R.id.b2_reference,"Source fictive");
    }

    @Test public void createReviseAndBrowseExactVersionHistoryFromHome() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.open_review)).perform(click()); newForm(); tap(R.id.b2_save);
            visible(allOf(withId(R.id.b2_title),withText("Article fictif")));
            assertEquals(root,lastWrite); assertEquals("French",str(sent,"language"));
            onView(withId(R.id.b2_approval)).check(matches(not(isDisplayed())));
            tap(R.id.b2_edit); visible(withId(R.id.b2_title)); enter(R.id.b2_title,"Titre révisé"); tap(R.id.b2_save);
            visible(allOf(withId(R.id.b2_title),withText("Titre révisé")));
            assertEquals(root+"/KB-0001",lastWrite); assertEquals(1,sent.size());
            onView(withId(R.id.b2_meta)).check(matches(withText(containsString("KB-0002"))));
            tap(R.id.b2_history); visible(withText("Article fictif")); assertEquals("KB-0002",historyId);
            openRow("Article fictif");
            onView(withId(R.id.b2_meta)).check(matches(withText(containsString("KB-0001"))));
            onView(withId(R.id.b2_edit)).check(matches(not(isDisplayed())));
            onView(withId(R.id.b2_notice)).check(matches(withText(R.string.kb_notice)));
        }
    }

    @Test public void clinicianReadsThenConfirmsApprovalAndRetirement() {
        create("Article fictif"); login("Clinical reviewer");
        try (ActivityScenario<ContentListActivity> screen = ActivityScenario.launch(ContentListActivity.class)) {
            openRow("Article fictif");
            onView(withId(R.id.b2_edit)).check(matches(not(isDisplayed())));
            enter(R.id.b2_expiry,"2031-02-30"); tap(R.id.b2_approve); assertEquals(0,writes);
            enter(R.id.b2_expiry,"2031-02-28"); tap(R.id.b2_approve);
            onView(withId(android.R.id.button2)).perform(click()); assertEquals(0,writes);
            tap(R.id.b2_approve); onView(withId(android.R.id.button1)).perform(click());
            visible(withText(R.string.b2_saved)); assertEquals(root+"/KB-0001",lastWrite);
            assertEquals("Clinically approved",str(sent,"approvalStatus")); assertEquals("2031-02-28",str(sent,"expiryDate"));
            onView(withId(R.id.b2_details)).perform(scrollTo()).check(matches(withText(containsString("CR-FICTIF"))));
            tap(R.id.b2_retire); onView(withId(android.R.id.button1)).perform(click());
            visible(allOf(withId(R.id.b2_meta),withText(containsString(context.getString(R.string.b2_retired)))));
            assertEquals("Retired",str(sent,"approvalStatus")); assertEquals(1,sent.size());
            onView(withId(R.id.b2_retire)).check(matches(not(isDisplayed())));
        }
    }

    @Test public void refusedDecisionPreservesReadAccessAndExpiredSessionClearsList() {
        create("Article fictif"); login(null); writeCode=403;
        try (ActivityScenario<ContentListActivity> screen = ActivityScenario.launch(ContentListActivity.class)) {
            openRow("Article fictif"); tap(R.id.b2_approve); onView(withId(android.R.id.button1)).perform(click());
            visible(withText(R.string.b2_no_access)); assertNotNull(SafetySession.current);
            onView(withId(R.id.b2_approve)).check(matches(not(isDisplayed())));
            tap(R.id.b2_history); visible(withText("Article fictif"));
            readCode=401; onView(withId(R.id.b3_reload)).perform(click()); visible(withText(R.string.b3_auth));
            onView(withText("Article fictif")).check(doesNotExist()); assertNull(SafetySession.current);
        }
    }

    @Test public void lostCreateResponseIsReconciledInListWithoutSecondPost() {
        loseReply=true;
        try (ActivityScenario<ContentListActivity> screen = ActivityScenario.launch(ContentListActivity.class)) {
            newForm(); tap(R.id.b2_save); visible(withText(R.string.b2_lost));
            onView(withId(R.id.b2_save)).check(matches(not(isDisplayed())));
            onView(withId(R.id.b2_recover)).perform(click()); visible(withText("Article fictif"));
            assertEquals(1,writes);
        }
    }

    @Test public void staleReviewerCannotApproveAReplacement() {
        JsonObject old = create("Ancienne"); login("Clinical reviewer");
        try (ActivityScenario<ContentListActivity> screen = ActivityScenario.launch(ContentListActivity.class)) {
            openRow("Ancienne");
            synchronized(this) { old.addProperty("approvalStatus","Retired"); create("Remplaçante"); }
            writeCode=409;
            tap(R.id.b2_approve); onView(withId(android.R.id.button1)).perform(click());
            visible(withText(R.string.b2_conflict)); assertEquals(root+"/KB-0001",lastWrite);
            onView(withId(R.id.b2_approve)).check(matches(not(isDisplayed())));
            tap(R.id.b2_history); openRow("Remplaçante");
            onView(withId(R.id.b2_meta)).check(matches(withText(containsString("KB-0002"))));
            assertEquals(1,writes);
        }
    }

    @Test public void pagesFiltersAndLanguageResources() {
        for(int i=1;i<=51;i++) create("Fiche "+i);
        try (ActivityScenario<ContentListActivity> screen = ActivityScenario.launch(ContentListActivity.class)) {
            visible(withId(R.id.b2_next)); onView(withId(R.id.b2_next)).perform(click()); visible(withText("Fiche 51"));
            assertEquals(50,offset);
            onView(withId(R.id.b2_filter)).perform(click()); onData(anything()).atPosition(2).perform(click());
            visible(withText(R.string.b2_empty)); assertEquals(0,offset); assertEquals("Clinically approved",filter);
            screen.recreate(); visible(withText(R.string.b2_empty)); assertEquals("Clinically approved",filter);
        }
        var cfg = new android.content.res.Configuration(context.getResources().getConfiguration());
        cfg.setLocale(new Locale("ar"));
        Context ar = context.createConfigurationContext(cfg);
        assertEquals(View.LAYOUT_DIRECTION_RTL,ar.getResources().getConfiguration().getLayoutDirection());
        assertEquals("المراجعة السريرية",ar.getString(R.string.b2_review));
    }
}
