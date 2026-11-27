package com.example.chifaaapp.chat;

import android.content.Context;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.ListView;
import java.util.concurrent.atomic.AtomicBoolean;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.chifaaapp.R;
import com.example.chifaaapp.knowledge.DeviceKnowledge;
import com.example.chifaaapp.knowledge.KnowledgeSession;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.*;
import org.junit.runner.RunWith;
import retrofit2.Call;
import retrofit2.Response;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class E1JourneyTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private JsonObject fixture;
    private ChatRepository chat;
    private String expectedToken;

    @Before public void load() throws Exception {
        File file = new File(context.getCacheDir(), "e1-device-fixture.json");
        if ("required".equals(InstrumentationRegistry.getArguments().getString("e1Live")))
            assertTrue("Fixture du serveur temporaire absente", file.isFile());
        else Assume.assumeTrue("Serveur lancé par E1DeviceCheck", file.isFile());
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            fixture = JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private boolean preserveCache;
    @After public void close() { if (!preserveCache) KnowledgeSession.close(); }

    private void open(String account, String token) throws Exception {
        open(account, token, null);
    }

    private void open(String account, String token, Runnable onUnauthorized) throws Exception {
        DeviceKnowledge.open(context, fixture, account, token, onUnauthorized);
        chat = KnowledgeSession.authenticatedClient().create(ChatRepository.class);
        expectedToken = fixture.get(token).getAsString();
    }

    private <T> T expect(int code, Call<T> call) throws Exception {
        Response<T> response = call.execute();
        try {
            assertTrue("Jeton inattendu sur la requête", ("Bearer " + expectedToken)
                    .equals(response.raw().request().header("Authorization")));
            assertEquals(code, response.code());
            return response.body();
        } finally {
            if (response.errorBody() != null) response.errorBody().close();
        }
    }

    private void onlySession(UUID id) throws Exception {
        List<ChatRepository.Session> list = expect(200, chat.sessions(0, 10));
        assertEquals(1, list.size());
        assertEquals(id, list.get(0).sessionId);
    }

    private void foreignConversation(UUID session, UUID answer) throws Exception {
        expect(404, chat.history(session, 0, 10));
        expect(404, chat.send(session, new ChatRepository.Question("Message étranger fictif")));
        expect(404, chat.feedback(answer, new ChatRepository.Feedback(1)));
    }

    @Test public void initialConsents() throws Exception {
        open("a", "a1");
        DeviceKnowledge.read("KB-0001", true);
        DeviceKnowledge.read("KB-0001", false);
        UUID sessionA = expect(201, chat.create()).sessionId;
        ChatRepository.Turn turnA = expect(200, chat.send(sessionA, new ChatRepository.Question("Alpha compte A")));
        assertEquals("KB-0001", turnA.answer.contentId);

        open("b", "b1");
        DeviceKnowledge.assertOfflineMissing("KB-0001");
        DeviceKnowledge.read("KB-0002", true);
        expect(403, chat.sessions(0, 10));
        expect(403, chat.create());
        open("a", "a1");
        DeviceKnowledge.read("KB-0001", true);
        preserveCache = true; // Reproduce restoration of the Android process before the first 401.
    }

    @Test public void reconnectAndSwitchAccounts() throws Exception {
        java.util.concurrent.CountDownLatch expired = new java.util.concurrent.CountDownLatch(1);
        open("a", "a1", expired::countDown);
        DeviceKnowledge.read("KB-0001", false);
        expect(401, chat.sessions(0, 10));
        assertTrue(expired.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertNull(KnowledgeSession.authenticatedClient());
        open("a", "a2");
        assertFalse(fixture.get("a1").equals(fixture.get("a2")));
        UUID sessionA = UUID.fromString(fixture.get("sessionA").getAsString());
        UUID answerA = UUID.fromString(fixture.get("answerA").getAsString());
        onlySession(sessionA);
        DeviceKnowledge.assertOfflineMissing("KB-0001");
        DeviceKnowledge.read("KB-0001", true);

        open("b", "b2");
        DeviceKnowledge.assertOfflineMissing("KB-0001");
        assertTrue(expect(200, chat.sessions(0, 10)).isEmpty());
        foreignConversation(sessionA, answerA);
        DeviceKnowledge.read("KB-0002", true);
        DeviceKnowledge.read("KB-0002", false);
        UUID sessionB = expect(201, chat.create()).sessionId;
        ChatRepository.Turn turnB = expect(200, chat.send(sessionB, new ChatRepository.Question("Alpha compte B")));
        onlySession(sessionB);

        open("a", "a2");
        DeviceKnowledge.assertOfflineMissing("KB-0002");
        DeviceKnowledge.assertOfflineMissing("KB-0001");
        onlySession(sessionA);
        foreignConversation(sessionB, turnB.answer.messageId);
        assertEquals("Alpha compte A", expect(200, chat.history(sessionA, 0, 10)).get(0).text);
    }

    @Test public void withdrawHealthConsent() throws Exception {
        open("a", "a2");
        UUID session = UUID.fromString(fixture.get("sessionA").getAsString());
        UUID answer = UUID.fromString(fixture.get("answerA").getAsString());
        expect(403, chat.sessions(0, 10));
        expect(403, chat.create());
        expect(403, chat.history(session, 0, 10));
        expect(403, chat.send(session, new ChatRepository.Question("Après retrait")));
        expect(403, chat.feedback(answer, new ChatRepository.Feedback(5)));

        ChatRepository.Session item = new ChatRepository.Session();
        item.sessionId = session;
        try (ActivityScenario<ChatActivity> screen = ActivityScenario.launch(ChatActivity.intent(context, item))) {
            AtomicBoolean displayed = new AtomicBoolean();
            long end = SystemClock.elapsedRealtime() + 30000;
            do {
                screen.onActivity(activity -> {
                    TextView status = activity.findViewById(R.id.chat_status);
                    displayed.set(status.isShown() && activity.getString(R.string.chat_consent).contentEquals(status.getText()));
                });
                if (!displayed.get()) SystemClock.sleep(100);
            } while (!displayed.get() && SystemClock.elapsedRealtime() < end);
            assertTrue("Message de consentement affiché", displayed.get());
            screen.onActivity(activity -> {
                ListView messages = activity.findViewById(R.id.chat_list);
                assertEquals(0, messages.getCount());
                assertFalse(activity.findViewById(R.id.chat_input).isEnabled());
            });
        }
        DeviceKnowledge.assertListedOnline("KB-0001");
        DeviceKnowledge.read("KB-0001", true);
        DeviceKnowledge.read("KB-0001", false);

        open("b", "b2");
        DeviceKnowledge.assertOfflineMissing("KB-0001");
        List<ChatRepository.Session> sessionsB = expect(200, chat.sessions(0, 10));
        assertEquals(1, sessionsB.size());
        UUID own = sessionsB.get(0).sessionId;
        assertNotEquals(session, own);
        assertEquals("Alpha compte B", expect(200, chat.history(own, 0, 10)).get(0).text);
        expect(200, chat.send(own, new ChatRepository.Question("Deuxième question fictive B")));
    }
}
