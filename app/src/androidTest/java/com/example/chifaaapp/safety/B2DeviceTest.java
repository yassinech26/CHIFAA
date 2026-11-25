package com.example.chifaaapp.safety;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.chifaaapp.R;
import com.example.chifaaapp.clinicalreview.ContentListActivity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.*;
import okhttp3.OkHttpClient;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class B2DeviceTest {
    private JsonObject fixture;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private void login(String token,String role) throws Exception {
        String url = fixture.get("url").getAsString();
        assertTrue(url.matches("https://localhost:[0-9]+/"));
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null);
        try (InputStream pem = new ByteArrayInputStream(fixture.get("certificate").getAsString().getBytes(StandardCharsets.US_ASCII))) {
            trust.setCertificateEntry("local",CertificateFactory.getInstance("X.509").generateCertificate(pem));
        }
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tm.init(trust);
        X509TrustManager manager = (X509TrustManager)tm.getTrustManagers()[0];
        SSLContext tls = SSLContext.getInstance("TLS"); tls.init(null,new TrustManager[]{manager},null);
        SafetySession.open(url,fixture.get(token).getAsString(),role,
                new OkHttpClient.Builder().sslSocketFactory(tls.getSocketFactory(),manager));
    }

    private void waitFor(Matcher<View> view) {
        long deadline = SystemClock.elapsedRealtime()+15000;
        while (true) {
            try { onView(view).check(matches(isDisplayed())); return; }
            catch (AssertionError | NoMatchingViewException waiting) {
                if (SystemClock.elapsedRealtime() >= deadline) throw waiting;
                SystemClock.sleep(100);
            }
        }
    }
    private void enter(int id,String text) { onView(withId(id)).perform(scrollTo(),replaceText(text),closeSoftKeyboard()); }
    private void tap(int id) { onView(withId(id)).perform(scrollTo(),click()); }
    @After public void logout() { SafetySession.close(); }

    @Test public void realServerPreservesVersionsAndRecordsClinicalDecision() throws Exception {
        File config = new File(context.getCacheDir(),"b2-device-fixture.json");
        if ("required".equals(InstrumentationRegistry.getArguments().getString("b2Live"))) {
            assertTrue("Configuration du serveur temporaire absente",config.isFile());
        } else {
            Assume.assumeTrue("Serveur temporaire lancé par B2DeviceCheck",config.isFile());
        }
        try (Reader in = new InputStreamReader(new FileInputStream(config),StandardCharsets.UTF_8)) {
            fixture = JsonParser.parseReader(in).getAsJsonObject();
        }
        login("editor","Content team");
        try (ActivityScenario<ContentListActivity> list = ActivityScenario.launch(ContentListActivity.class)) {
            waitFor(withId(R.id.b2_create)); onView(withId(R.id.b2_create)).perform(click());
            enter(R.id.b2_title,"Atlas appareil"); enter(R.id.b2_topic,"Accueil"); enter(R.id.b2_body,"Texte fictif");
            enter(R.id.b2_source,"Test A"); enter(R.id.b2_reference,"Source temporaire"); tap(R.id.b2_save);
            waitFor(allOf(withId(R.id.b2_title),withText("Atlas appareil")));
            tap(R.id.b2_edit); waitFor(withId(R.id.b2_title)); enter(R.id.b2_title,"Atlas révisé"); tap(R.id.b2_save);
            waitFor(allOf(withId(R.id.b2_title),withText("Atlas révisé")));
            onView(withId(R.id.b2_meta)).check(matches(withText(containsString("v1.1"))));
            tap(R.id.b2_history); waitFor(withText("Atlas appareil")); waitFor(withText("Atlas révisé"));
            login("reviewer","Clinical reviewer");
            waitFor(withText(context.getString(R.string.b2_list)));
            waitFor(withText("Atlas révisé")); onView(withText("Atlas révisé")).perform(click());
            waitFor(withId(R.id.b2_body)); tap(R.id.b2_approve); onView(withId(android.R.id.button1)).perform(click());
            waitFor(withText(R.string.b2_saved));
            onView(withId(R.id.b2_details)).perform(scrollTo()).check(matches(withText(containsString("CR-E2E"))));
            tap(R.id.b2_retire); onView(withId(android.R.id.button1)).perform(click());
            waitFor(allOf(withId(R.id.b2_meta),withText(containsString(context.getString(R.string.b2_retired)))));
        }
    }
}
