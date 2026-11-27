package com.example.chifaaapp.patient;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.chifaaapp.R;
import com.example.chifaaapp.knowledge.KnowledgeSession;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import retrofit2.mock.Calls;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PatientJourneyUiTest {
    private final PatientRepository repository=PatientRepository.INSTANCE;
    private final PatientApi original=repository.api;
    private Models.Register submitted;
    private Models.Checkin checkin;
    @Before public void setup() {
        repository.close();
        repository.api=(PatientApi)Proxy.newProxyInstance(PatientApi.class.getClassLoader(),new Class<?>[]{PatientApi.class},(p,m,args)->{
            switch (m.getName()) {
                case "register":
                    submitted=(Models.Register)args[0];
                    Models.Challenge challenge=new Models.Challenge(); challenge.challengeId="test";
                    return Calls.response(challenge);
                case "verify":
                    return Calls.response(fictionalSession());
                case "profile": return Calls.response(submitted.profile);
                case "consents": return Calls.response(Collections.emptyList());
                case "catalog":
                    Models.Catalog catalog=new Models.Catalog();
                    catalog.sideEffects=Arrays.asList("AT-073-test-A","AT-073-test-B");
                    return Calls.response(catalog);
                case "create": checkin=(Models.Checkin)args[0]; checkin.id="test"; return Calls.response(checkin);
                case "one": return Calls.response(checkin);
                default: throw new AssertionError(m.getName());
            }
        });
    }
    @After public void restore() { repository.api=original; repository.close(); }
    private Models.Session fictionalSession() {
        Models.Session session = new Models.Session();
        session.token = "fictional";
        session.userPid = "00000000-0000-0000-0000-000000000001";
        return session;
    }
    private void press(int id) { onView(withId(id)).perform(scrollTo(),click()); }
    @Test public void registration() {
        try (var screen=ActivityScenario.launch(WelcomeActivity.class)) {
            press(R.id.sign_up); press(R.id.adult_no);
            onView(withId(R.id.message)).check(matches(withText(R.string.minor)));
            press(R.id.adult_yes); press(R.id.agree); press(R.id.decline); press(R.id.agree);
            onView(withId(R.id.phone)).perform(replaceText("+19990000000001"),closeSoftKeyboard());
            for (int id:new int[]{R.id.age_band,R.id.governorate,R.id.language}) {
                press(id); onData(anything()).atPosition(1).perform(click());
            }
            press(R.id.send_code);
            onView(withId(R.id.otp_code)).perform(replaceText("123456"),closeSoftKeyboard());
            press(R.id.verify);
            onView(withId(R.id.my_data)).check(matches(isDisplayed()));
            assertNotNull(KnowledgeSession.authenticatedClient());
            press(R.id.open_library);
            onView(withId(R.id.kb_heading)).check(matches(isDisplayed()));
            pressBack();
            press(R.id.open_chat);
            onView(withId(R.id.chat_heading)).check(matches(isDisplayed()));
            pressBack();
            assertTrue(submitted.adult && submitted.service && submitted.survey);
            assertFalse(submitted.health);
        }
    }
    @Test public void privateCheckin() {
        assertTrue(repository.authenticate(fictionalSession()));
        try (var screen=ActivityScenario.launch(CreateCheckinActivity.class)) {
            onView(withText("AT-073-test-B")).perform(scrollTo(),click());
            press(R.id.save);
            onView(withId(R.id.result)).check(matches(withText(containsString("AT-073-test-B"))));
            assertEquals(Collections.singletonList("AT-073-test-B"),checkin.effects);
        }
    }
}
