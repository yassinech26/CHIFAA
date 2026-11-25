package com.example.chifaaapp.knowledge;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.chifaaapp.MainActivity;
import com.example.chifaaapp.R;
import com.example.chifaaapp.chat.ChatActivity;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.*;
import okio.Buffer;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ChatFlowTest {
    private static final String RESOURCE = "Texte de démonstration.\nRessource fictive CHIFAA.";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String session = UUID.randomUUID().toString(), answerId = UUID.randomUUID().toString();
    private final Gson json = new Gson();
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final List<String> attempts = new ArrayList<>();
    private volatile boolean created, dropReply, withdrawn;
    private volatile int access = 200, feedbackCode = 204;
    private String kind = "Knowledge";

    private Map<String, Object> object(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) value.put((String)pairs[i], pairs[i + 1]);
        return value;
    }

    private Map<String, Object> message(String id, String role, String text) {
        return object("messageId", id, "sessionId", session, "senderRole", role, "text", text);
    }

    @Before public void connect() {
        KnowledgeSession.open(context, "https://m5.invalid/", UUID.randomUUID().toString(), "m5-test",
                new OkHttpClient.Builder().addInterceptor(chain -> serve(chain.request())));
    }

    private synchronized Response serve(Request request) throws IOException {
        String path = request.url().encodedPath();
        boolean post = request.method().equals("POST");
        String base = "/api/chat/sessions";
        Map<String, Object> header = object("sessionId", session, "startedAt", "2026-06-01T12:00:00Z");
        int code = access;
        Object value = null;
        if (!"Bearer m5-test".equals(request.header("Authorization"))) code = 401;
        else if (code == 200) {
            if (path.equals(base) && post) { created = true; code = 201; value = header; }
            else if (path.equals(base)) { value = created ? new Object[]{header} : new Object[]{}; }
            else if (path.equals(base + "/" + session + "/messages") && post) {
                Map<?, ?> data = body(request);
                String id = (String)data.get("messageId");
                attempts.add(UUID.fromString(id).toString());
                if (history.isEmpty()) {
                    history.add(message(id, "User", (String)data.get("text")));
                    Map<String, Object> response = message(answerId, "AI", kind.equals("Distress") ? RESOURCE : "Réponse de démonstration.");
                    response.put("replyKind", kind);
                    if (kind.equals("Knowledge")) response.put("contentId", "KB-0001");
                    history.add(response);
                }
                if (dropReply) { dropReply = false; throw new IOException("Fixture"); }
                value = object("question", history.get(0), "answer", history.get(1));
            } else if (path.equals(base + "/" + session + "/messages")) {
                int offset = Integer.parseInt(request.url().queryParameter("offset"));
                int limit = Integer.parseInt(request.url().queryParameter("limit"));
                value = history.subList(Math.min(offset, history.size()), Math.min(offset + limit, history.size()));
            } else if (path.equals("/api/chat/messages/" + answerId + "/feedback") && post) {
                code = feedbackCode;
                if (code == 204) history.get(1).put("userFeedback", ((Number)body(request).get("rating")).intValue());
            } else if (path.equals("/api/kb/KB-0001") && !post) {
                if (withdrawn) code = 404;
                else value = object("contentId","KB-0001","title","Fiche fictive","topic","Accueil","language","French",
                        "fictional",true,"contentText","Détail fictif","sourceType","Test","sourceReference","Source de test",
                        "reviewDate","2020-01-01","contentVersion","v1.0");
            } else code = 404;
        }
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                .body(ResponseBody.create(MediaType.get("application/json"), code == 204 ? "" : json.toJson(value))).build();
    }

    private Map<?, ?> body(Request request) throws IOException {
        Buffer data = new Buffer();
        request.body().writeTo(data);
        return json.fromJson(data.readUtf8(), Map.class);
    }

    @After public void disconnect() { KnowledgeSession.close(); }

    private void visible(Matcher<View> matcher) {
        long until = SystemClock.elapsedRealtime() + 10000;
        while (true) {
            try { onView(matcher).check(matches(isDisplayed())); return; }
            catch (NoMatchingViewException | AssertionError waiting) {
                if (SystemClock.elapsedRealtime() >= until) throw waiting;
                SystemClock.sleep(100);
            }
        }
    }

    private void typeAndSend(String text) {
        onView(withId(R.id.chat_input)).perform(replaceText(text), closeSoftKeyboard());
        onView(withId(R.id.chat_send)).perform(click());
    }

    private ActivityScenario<ChatActivity> conversation() {
        created = true;
        return ActivityScenario.launch(new Intent(context, ChatActivity.class).putExtra("chat.session", session));
    }

    @Test public void createOpenReferenceAndReplaceRatingFromHistory() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.open_chat)).perform(click());
            visible(withText(R.string.chat_empty));
            onView(withId(R.id.chat_new)).perform(click());
            visible(withText(R.string.chat_first));
            assertTrue(created);
            typeAndSend("Une fiche fictive");
            visible(withText(containsString("Source de test")));
            onView(allOf(withId(R.id.chat_reference), isDisplayed())).perform(click());
            visible(withText("Détail fictif"));
            pressBack();
            visible(withText(containsString("Source de test")));
            onView(allOf(withId(R.id.chat_rate), isDisplayed())).perform(click());
            onView(withText("1")).perform(click());
            visible(withText(context.getString(R.string.chat_rating, 1)));
            pressBack();
            visible(withText(startsWith("Conversation du ")));
            onView(withText(startsWith("Conversation du "))).perform(click());
            visible(withText(context.getString(R.string.chat_rating, 1)));
            onView(allOf(withId(R.id.chat_rate), isDisplayed())).perform(click());
            onView(withText("5")).perform(click());
            visible(withText(context.getString(R.string.chat_rating, 5)));
            assertEquals(5, history.get(1).get("userFeedback"));
        }
    }

    @Test public void retryAfterLostReplyUsesSameIdAndDisplaysExactDistressResources() {
        dropReply = true;
        kind = "Distress";
        try (ActivityScenario<ChatActivity> screen = conversation()) {
            visible(withText(R.string.chat_first));
            typeAndSend("Message de test");
            visible(withText(R.string.chat_pending));
            onView(withId(R.id.chat_input)).check(matches(not(isEnabled())));
            onView(withId(R.id.chat_retry)).perform(click());
            visible(withText(RESOURCE));
            visible(allOf(withId(R.id.chat_resource_heading), isDisplayed()));
            assertEquals(2, attempts.size());
            assertEquals(attempts.get(0), attempts.get(1));
            screen.recreate();
            visible(withText(RESOURCE));
            assertEquals(2, history.size());
        }
    }

    @Test public void withdrawnReferenceAndConsentRevocationRemoveSensitiveViews() {
        withdrawn = true;
        try (ActivityScenario<ChatActivity> screen = conversation()) {
            visible(withText(R.string.chat_first));
            typeAndSend("Question fictive");
            visible(withText(R.string.chat_unavailable));
            onView(withText("Réponse de démonstration.")).check(doesNotExist());
            onView(withId(R.id.chat_input)).perform(replaceText("Brouillon"), closeSoftKeyboard());
            access = 403;
            screen.recreate();
            visible(withText(R.string.chat_consent));
            onView(withText("Brouillon")).check(doesNotExist());
            onView(withText("Question fictive")).check(doesNotExist());
            assertNotNull(KnowledgeSession.authenticatedClient());
        }
    }

    @Test public void historyPaginationAndExpiredAuthentication() {
        for (int i = 1; i <= 51; i++) history.add(message(UUID.randomUUID().toString(), "User", "Message " + i));
        try (ActivityScenario<ChatActivity> screen = conversation()) {
            visible(withId(R.id.chat_next));
            onView(withId(R.id.chat_next)).perform(click());
            visible(withText("Message 51"));
            onView(withId(R.id.chat_previous)).check(matches(isDisplayed()));
            access = 401;
            onView(withId(R.id.chat_previous)).perform(click());
            visible(withText(R.string.kb_auth));
            onView(withText("Message 51")).check(doesNotExist());
            assertNull(KnowledgeSession.authenticatedClient());
        }
    }

    @Test public void failedFeedbackIsNeverShownAsSaved() {
        feedbackCode = 503;
        try (ActivityScenario<ChatActivity> screen = conversation()) {
            visible(withText(R.string.chat_first));
            typeAndSend("Test de note");
            visible(withText(containsString("Source de test")));
            onView(allOf(withId(R.id.chat_rate), isDisplayed())).perform(click());
            onView(withText("4")).perform(click());
            visible(withText(R.string.chat_feedback_error));
            assertNull(history.get(1).get("userFeedback"));
            onView(allOf(withId(R.id.chat_rate), isDisplayed())).check(matches(withText(R.string.chat_rate)));
        }
    }

    @Test public void rotationReconcilesAnUnconfirmedSendWithServerHistory() {
        kind = "Directory";
        dropReply = true;
        try (ActivityScenario<ChatActivity> screen = conversation()) {
            visible(withText(R.string.chat_first));
            typeAndSend("Question de démonstration");
            visible(withText(R.string.chat_pending));
            screen.recreate();
            visible(withText("Réponse de démonstration."));
            onView(withId(R.id.chat_input)).check(matches(allOf(isEnabled(), withText(""))));
            onView(withId(R.id.chat_retry)).check(matches(not(isDisplayed())));
            assertEquals(1, attempts.size());
        }
    }

    @Test public void arabicDistressTextAndBubbleDirectionFollowRtl() {
        String guidance = "نص توجيهي للاختبار";
        history.add(message(UUID.randomUUID().toString(), "User", "رسالة اختبار"));
        Map<String, Object> answer = message(answerId, "AI", guidance);
        answer.put("replyKind", "Distress");
        history.add(answer);
        android.content.res.Configuration config = new android.content.res.Configuration(context.getResources().getConfiguration());
        config.setLocale(new java.util.Locale("ar"));
        assertEquals("موارد المساعدة", context.createConfigurationContext(config).getString(R.string.chat_distress));
        try (ActivityScenario<ChatActivity> screen = conversation()) {
            visible(withText(guidance));
            screen.onActivity(activity -> activity.findViewById(R.id.chat_root).setLayoutDirection(View.LAYOUT_DIRECTION_RTL));
            onView(withText("رسالة اختبار")).check((view, missing) -> assertBubble(view, android.view.Gravity.LEFT));
            onView(withText(guidance)).check((view, missing) -> assertBubble(view, android.view.Gravity.RIGHT));
            onView(allOf(withId(R.id.chat_reference), isDisplayed())).check(doesNotExist());
        }
    }

    private void assertBubble(View body, int expected) {
        View bubble = (View)body.getParent();
        int gravity = ((android.widget.FrameLayout.LayoutParams)bubble.getLayoutParams()).gravity;
        assertEquals(expected, android.view.Gravity.getAbsoluteGravity(gravity, bubble.getLayoutDirection())
                & android.view.Gravity.HORIZONTAL_GRAVITY_MASK);
    }
}
