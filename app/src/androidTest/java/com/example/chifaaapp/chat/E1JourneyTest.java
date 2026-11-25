package com.example.chifaaapp.chat;

import android.content.Context;
import android.os.SystemClock;
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
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
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

    @After public void close() { KnowledgeSession.close(); }

    private void open(String account, String token) throws Exception {
        DeviceKnowledge.open(context, fixture, account, token);
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

    @Test public void refreshAndSwitchAccounts() throws Exception {
        open("a", "expired");
        expect(401, chat.sessions(0, 10));

        open("a", "a1");
        DeviceKnowledge.read("KB-0001", true);
        UUID sessionA = expect(201, chat.create()).sessionId;
        ChatRepository.Turn turnA = expect(200, chat.send(sessionA, new ChatRepository.Question("Alpha compte A")));
        assertEquals("KB-0001", turnA.answer.contentId);

        assertFalse("Deux jetons distincts attendus", fixture.get("a1").equals(fixture.get("a2")));
        open("a", "a2");
        DeviceKnowledge.read("KB-0001", false);
        onlySession(sessionA);
        assertEquals(turnA.question.messageId, expect(200, chat.history(sessionA, 0, 10)).get(0).messageId);

        open("b", "b1");
        DeviceKnowledge.assertOfflineMissing("KB-0001");
        assertTrue(expect(200, chat.sessions(0, 10)).isEmpty());
        foreignConversation(sessionA, turnA.answer.messageId);
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
            long end = SystemClock.elapsedRealtime() + 15000;
            while (true) {
                try {
                    onView(withId(R.id.chat_status)).check(matches(withText(R.string.chat_consent)));
                    break;
                } catch (AssertionError loading) {
                    if (SystemClock.elapsedRealtime() >= end) throw loading;
                    SystemClock.sleep(100);
                }
            }
            onView(withText("Alpha compte A")).check(doesNotExist());
            onView(withId(R.id.chat_input)).check(matches(not(isEnabled())));
        }
        DeviceKnowledge.assertListedOnline("KB-0001");
        DeviceKnowledge.read("KB-0001", true);
        DeviceKnowledge.read("KB-0001", false);

        open("b", "b1");
        DeviceKnowledge.assertOfflineMissing("KB-0001");
        List<ChatRepository.Session> sessionsB = expect(200, chat.sessions(0, 10));
        assertEquals(1, sessionsB.size());
        UUID own = sessionsB.get(0).sessionId;
        assertNotEquals(session, own);
        assertEquals("Alpha compte B", expect(200, chat.history(own, 0, 10)).get(0).text);
        expect(200, chat.send(own, new ChatRepository.Question("Deuxième question fictive B")));
    }
}
