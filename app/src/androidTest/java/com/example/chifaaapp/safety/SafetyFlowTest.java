package com.example.chifaaapp.safety;

import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.chifaaapp.MainActivity;
import com.example.chifaaapp.R;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
public class SafetyFlowTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<SafetyRepository.Event> events = new ArrayList<>();
    private final Gson gson = new Gson();
    private SafetyRepository.Event first;
    private volatile JsonObject sent;
    private volatile int patchCode = 200, readCode = 200, writes, offsetSeen;
    private volatile boolean loseResponse;
    private volatile String filterSeen;

    @Before public void setup() {
        first = event("Signal fictif A","Critical");
        event("Signal fictif B","High");
        connect();
    }

    private SafetyRepository.Event event(String name, String severity) {
        SafetyRepository.Event e = new SafetyRepository.Event();
        e.eventId = UUID.randomUUID();
        e.triggerType = name;
        e.severity = severity;
        e.status = "Open";
        e.flaggedAt = "2026-01-02T01:10:10.123456Z";
        events.add(e);
        return e;
    }

    private void connect() {
        SafetySession.open("https://safety.invalid/","test-staff",new OkHttpClient.Builder()
                .addInterceptor(chain -> serve(chain.request())));
    }

    @After public void logout() { SafetySession.close(); }

    private synchronized Response serve(Request req) throws IOException {
        String root = "/api/admin/safety-events";
        String path = req.url().encodedPath();
        String body = "{}";
        int code = 404;
        if (!"Bearer test-staff".equals(req.header("Authorization"))) code = 401;
        else if (req.method().equals("GET") && path.equals(root)) {
            code = readCode;
            filterSeen = req.url().queryParameter("status");
            offsetSeen = Integer.parseInt(req.url().queryParameter("offset"));
            int limit = Integer.parseInt(req.url().queryParameter("limit"));
            List<SafetyRepository.Event> matching = new ArrayList<>();
            for (SafetyRepository.Event e : events)
                if (filterSeen == null || e.status.equals(filterSeen)) matching.add(e);
            body = gson.toJson(matching.subList(Math.min(offsetSeen, matching.size()), Math.min(offsetSeen+limit, matching.size())));
        } else if (req.method().equals("PATCH") && path.equals(root+"/"+first.eventId)) {
            Buffer data = new Buffer();
            req.body().writeTo(data);
            sent = JsonParser.parseString(data.readUtf8()).getAsJsonObject();
            writes++;
            code = patchCode;
            if (code == 200) {
                if (sent.has("reviewerId")) first.reviewerId = value("reviewerId");
                if (sent.has("orgId")) first.orgId = value("orgId");
                if (sent.has("actionTaken")) first.actionTaken = value("actionTaken");
                if (sent.has("referralOutcome")) first.referralOutcome = value("referralOutcome");
                if (sent.has("status")) {
                    first.status = value("status");
                    if (first.status.equals("In progress")) {
                        if (first.reviewerId == null) first.reviewerId = "SR-01";
                        first.takenInChargeAt = "2026-01-02T01:11:15Z";
                        first.handlingDelaySeconds = 65L;
                    }
                    if (first.status.equals("Resolved")) {
                        first.resolvedAt = "2026-01-02T02:10:10Z";
                        first.resolutionDelaySeconds = 3600L;
                    }
                }
                body = gson.toJson(first);
                if (loseResponse) { loseResponse = false; throw new IOException("Fixture"); }
            }
        }
        return new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                .body(ResponseBody.create(MediaType.get("application/json"), body)).build();
    }

    private String value(String key) { return sent.get(key).isJsonNull() ? null : sent.get(key).getAsString(); }

    private void visible(Matcher<View> matcher) {
        long until = SystemClock.elapsedRealtime()+12000;
        while (true) {
            try { onView(matcher).check(matches(isDisplayed())); return; }
            catch (AssertionError | NoMatchingViewException waiting) {
                if (SystemClock.elapsedRealtime() >= until) throw waiting;
                SystemClock.sleep(80);
            }
        }
    }

    private void open() {
        visible(withText("Signal fictif A"));
        onView(withText("Signal fictif A")).perform(click());
        visible(allOf(withId(R.id.b3_trigger),withText("Signal fictif A")));
    }

    private void choose(int spinner, int index) {
        if (spinner == R.id.b3_filter) onView(withId(spinner)).perform(click());
        else onView(withId(spinner)).perform(scrollTo(),click());
        onData(anything()).atPosition(index).perform(click());
    }

    private void submit() { onView(withId(R.id.b3_save)).perform(scrollTo(),click()); }

    private void accepted() {
        visible(withText(R.string.b3_confirmed));
        // Les réponses locales sont asynchrones, attendre aussi le libellé du statut confirmé.
        onView(withId(R.id.b3_meta)).perform(scrollTo()).check(matches(withText(new SafetyLabels(context).meta(first))));
    }

    @Test public void mainFilterAssignmentFullLifecycleAndDelays() {
        try (ActivityScenario<MainActivity> home = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.open_safety)).perform(click());
            visible(withText("Signal fictif A"));
            choose(R.id.b3_filter,1);
            open();
            assertEquals("Open",filterSeen);
            onView(withId(R.id.b3_delays)).perform(scrollTo()).check(matches(withText(containsString(context.getString(R.string.b3_pending)))));
            onView(withId(R.id.b3_reviewer)).perform(scrollTo(),replaceText("SR-02"),closeSoftKeyboard());
            submit(); accepted();
            assertEquals(1,sent.size()); assertEquals("SR-02",value("reviewerId"));
            choose(R.id.b3_status,1); submit(); accepted();
            assertEquals("In progress",first.status);
            String handling = new SafetyLabels(context).delay(65L);
            onView(withId(R.id.b3_delays)).perform(scrollTo()).check(matches(withText(containsString(handling))));
            choose(R.id.b3_action,3);
            choose(R.id.b3_outcome,1);
            onView(withId(R.id.b3_org)).perform(scrollTo(),replaceText("ORG-001"),closeSoftKeyboard());
            choose(R.id.b3_status,1); submit(); accepted();
            assertEquals("Referral",value("actionTaken")); assertEquals("Contacted",value("referralOutcome"));
            assertEquals("ORG-001",value("orgId"));
            choose(R.id.b3_status,1); submit(); accepted();
            assertEquals("Closed",first.status);
            onView(withId(R.id.b3_form)).check(matches(not(isDisplayed())));
            onView(withId(R.id.b3_summary)).perform(scrollTo()).check(matches(withText(containsString("SR-02"))));
            onView(withId(R.id.b3_delays)).perform(scrollTo()).check(matches(withText(containsString(new SafetyLabels(context).delay(3600L)))));
            pressBack();
            visible(withText("Signal fictif B"));
            onView(withText("Signal fictif A")).check(doesNotExist());
            choose(R.id.b3_filter,4);
            visible(withText("Signal fictif A"));
            assertEquals("Closed",filterSeen);
        }
    }

    @Test public void serverConfirmationRequiredAndAmbiguousPatchIsReadBack() {
        try (ActivityScenario<SafetyQueueActivity> queue = ActivityScenario.launch(SafetyQueueActivity.class)) {
            open();
            choose(R.id.b3_status,1);
            loseResponse = true;
            submit();
            visible(withText(R.string.b3_uncertain));
            onView(withId(R.id.b3_form)).check(matches(not(isDisplayed())));
            onView(withId(R.id.b3_reload)).perform(click());
            visible(withId(R.id.b3_trigger));
            onView(withId(R.id.b3_reviewer)).perform(scrollTo()).check(matches(withText("SR-01")));
            assertEquals(1,writes);
            assertFalse(sent.has("reviewerId"));
            patchCode = 409;
            choose(R.id.b3_status,1); submit();
            visible(withText(R.string.b3_conflict));
            assertEquals("In progress",first.status);
        }
    }

    @Test public void invalidReviewerCanBeCorrectedAndExplicitNullClearsOrg() {
        first.orgId = "ORG-001";
        try (ActivityScenario<SafetyQueueActivity> queue = ActivityScenario.launch(SafetyQueueActivity.class)) {
            open();
            patchCode = 400;
            onView(withId(R.id.b3_reviewer)).perform(scrollTo(),replaceText("OLD"),closeSoftKeyboard());
            submit(); visible(withText(R.string.b3_invalid));
            patchCode = 200;
            onView(withId(R.id.b3_reviewer)).perform(scrollTo(),replaceText("SR-02"),closeSoftKeyboard());
            onView(withId(R.id.b3_org)).perform(scrollTo(),replaceText(""),closeSoftKeyboard());
            submit(); accepted();
            assertTrue(sent.get("orgId").isJsonNull());
            assertEquals("SR-02", first.reviewerId);
        }
    }

    @Test public void roleRefusalPurgesScreenAndSession() {
        try (ActivityScenario<SafetyQueueActivity> queue = ActivityScenario.launch(SafetyQueueActivity.class)) {
            open();
            patchCode = 403;
            choose(R.id.b3_status,1); submit();
            visible(withText(R.string.b3_forbidden));
            onView(withText("Signal fictif A")).check(doesNotExist());
            assertNull(SafetySession.current);
            pressBack();
            visible(withText(R.string.b3_auth));
            onView(withText("Signal fictif A")).check(doesNotExist());
        }
    }

    @Test public void emptyFilterPaginationAndExpiredSession() {
        for (int i=3;i<=51;i++) event("Signal "+i,"Low");
        try (ActivityScenario<SafetyQueueActivity> queue = ActivityScenario.launch(SafetyQueueActivity.class)) {
            visible(withId(R.id.b3_next));
            onView(withId(R.id.b3_next)).perform(click());
            visible(withText("Signal 51")); assertEquals(50,offsetSeen);
            choose(R.id.b3_filter,3);
            visible(withText(R.string.b3_empty)); assertEquals("Resolved",filterSeen);
            readCode = 401;
            onView(withId(R.id.b3_reload)).perform(click());
            visible(withText(R.string.b3_auth)); assertNull(SafetySession.current);
        }
    }

    @Test public void movedThenRemovedEventAndArabicLabels() {
        try (ActivityScenario<SafetyQueueActivity> queue = ActivityScenario.launch(SafetyQueueActivity.class)) {
            visible(withText("Signal fictif A"));
            choose(R.id.b3_filter,1);
            open();
            first.status = "Closed";
            onView(withId(R.id.b3_reload)).perform(click());
            visible(allOf(withId(R.id.b3_meta),withText(new SafetyLabels(context).meta(first))));
            assertNull(filterSeen);
            events.remove(first);
            onView(withId(R.id.b3_reload)).perform(click());
            visible(withText(R.string.b3_missing));
            onView(withId(R.id.b3_content)).check(matches(not(isDisplayed())));
        }
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(new Locale("ar"));
        Context arabic = context.createConfigurationContext(config);
        assertEquals(View.LAYOUT_DIRECTION_RTL,arabic.getResources().getConfiguration().getLayoutDirection());
        assertEquals("مغلق",new SafetyLabels(arabic).label("Closed",SafetyLabels.STATES,R.array.b3_states));
        assertNotEquals(new SafetyLabels(arabic).delay(0L),new SafetyLabels(arabic).delay(null));
    }

    @Test public void staffSessionReplacementClosesPreviousDetail() {
        try (ActivityScenario<SafetyQueueActivity> queue = ActivityScenario.launch(SafetyQueueActivity.class)) {
            open();
            synchronized (this) {
                events.clear();
                event("File du second compte","Low");
            }
            connect();
            visible(withText("File du second compte"));
            onView(withText("Signal fictif A")).check(doesNotExist());
            assertEquals(0,writes);
        }
    }
}
