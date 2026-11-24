package com.example.chifaaapp;

import android.content.res.Configuration;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.chifaaapp.local.RetrofitClient;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.Locale;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class Student3UiTest {
    @Before public void useFrench() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(android.app.LocaleManager.class)
                    .setApplicationLocales(android.os.LocaleList.forLanguageTags("fr"));
        }
    }
    @After public void clearSession() { RetrofitClient.clear(); }

    @Test public void homeShowsPublicDirectoryAndSeparateStaffLogin() {
        try (var screen = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(R.string.e3_home)).check(matches(isDisplayed()));
            onView(withText(R.string.e3_directory)).check(matches(isDisplayed()));
            onView(withText(R.string.e3_staff_login)).check(matches(isDisplayed()));
            capture("01-home");
        }
    }

    @Test public void arabicResourcesAndDirectionAreAvailable() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(new Locale("ar"));
        var arabic = context.createConfigurationContext(config);
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL,arabic.getResources().getConfiguration().getLayoutDirection());
        assertEquals("دليل الخدمات",arabic.getString(R.string.e3_directory));
    }

    @Test public void userCanSendDemoDistressMessageAndOpenSupportDirectory() {
        String secret = InstrumentationRegistry.getArguments().getString("chifaaDemoPassword");
        Assume.assumeTrue("Start the local demo server and provide the instrumentation argument",secret != null && !secret.isEmpty());
        try (var screen = ActivityScenario.launch(MainActivity.class)) {
            login(R.string.e3_account,"demo.user",secret);
            awaitDisplayed(R.string.e3_history);
            onView(withText(R.string.e3_chat)).perform(click());
            capture("02-chat");
            onView(withId(R.id.draft))
                    .perform(replaceText("Scénario détresse démo"),closeSoftKeyboard());
            onView(withText(R.string.e3_send)).perform(click());
            awaitDisplayed(R.string.e3_support);
            capture("05-distress");
            onView(withContentDescription(R.string.e3_rating)).perform(click());
            capture("04-chat-feedback");
            onView(withText(R.string.e3_directory)).perform(click());
            awaitDisplayed(R.string.e3_refresh);
            androidx.test.espresso.Espresso.pressBack();
            onView(withText(R.string.e3_history)).perform(click());
            awaitDisplayed(allOf(withText(org.hamcrest.Matchers.startsWith("Ce que vous traversez")),
                    isDescendantOfA(withId(R.id.list))),"history preview");
            capture("03-chat-history");
        }
    }

    @Test public void reviewerCanOpenSafetyQueueThroughStaffDashboard() {
        String secret = InstrumentationRegistry.getArguments().getString("chifaaDemoPassword");
        Assume.assumeNotNull(secret);
        try (var screen = ActivityScenario.launch(MainActivity.class)) {
            login(R.string.e3_staff_login,"demo.safety_reviewer",secret);
            awaitDisplayed(R.string.e3_demo_admin);
            awaitDisplayed(R.string.e3_overview);
            capture("11-admin-dashboard");
            onView(withText(R.string.e3_manage)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            onView(withText(R.string.e3_queue)).perform(click());
            awaitDisplayed(R.string.e3_severity);
            onView(withContentDescription(R.string.e3_severity)).perform(click());
            onData(org.hamcrest.Matchers.is("Critique")).perform(click());
            onView(withText(R.string.e3_refresh)).perform(click());
            awaitDisplayedText("Critique");
            capture("12-safety-queue");
            onView(allOf(withText(R.string.e3_detail),isDisplayed())).perform(click());
            awaitDisplayed(R.string.e3_reviewer);
            capture("13-safety-details");
        }
    }

    @Test public void contentStaffCanCreateAnOrganisationFromAndroid() {
        String secret = InstrumentationRegistry.getArguments().getString("chifaaDemoPassword");
        Assume.assumeNotNull(secret);
        String name = "Démo UI " + java.util.UUID.randomUUID().toString().substring(0,8);
        try (var screen = ActivityScenario.launch(MainActivity.class)) {
            login(R.string.e3_staff_login,"demo.content_team",secret);
            awaitDisplayed(R.string.e3_manage);
            awaitDisplayed(R.string.e3_overview);
            onView(withText(R.string.e3_manage)).perform(click());
            awaitDisplayedText("Service fictif 1");
            capture("14-admin-directory");
            onView(withText(R.string.e3_add)).perform(click());
            capture("15-add-organisation");
            onView(withHint(R.string.e3_name)).perform(replaceText(name),closeSoftKeyboard());
            onView(withText(R.string.e3_save)).perform(click());
            onView(withText(R.string.e3_confirm)).check(matches(isDisplayed()));
            onView(withText(R.string.e3_save)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            awaitDisplayedText(name);
        }
    }

    private static void login(int entry, String username, String password) {
        onView(withText(entry)).perform(click());
        if (entry == R.string.e3_staff_login) capture("10-staff-login");
        onView(withHint(R.string.e3_username)).perform(replaceText(username));
        onView(withHint(R.string.e3_password)).perform(replaceText(password),closeSoftKeyboard());
        onView(withText(R.string.e3_login)).perform(click());
    }

    @Test public void publicDirectorySearchOpensServerDetails() {
        Assume.assumeNotNull(InstrumentationRegistry.getArguments().getString("chifaaDemoPassword"));
        try (var screen = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(R.string.e3_directory)).perform(click());
            awaitDisplayedText("Service fictif 1");
            capture("06-directory");
            onView(withContentDescription(R.string.e3_governorate)).perform(click());
            onData(org.hamcrest.Matchers.is("Zaghouan")).perform(click());
            onView(withContentDescription(R.string.e3_category)).perform(click());
            onData(org.hamcrest.Matchers.is("Assistance juridique")).perform(click());
            onView(withHint(R.string.e3_search)).perform(replaceText("Service fictif 30"),closeSoftKeyboard());
            awaitDisplayedText("Service fictif 30");
            capture("07-directory-filters");
            onView(withText(R.string.e3_detail)).perform(click());
            awaitDisplayed(R.string.e3_test_data);
            onView(withText(R.string.e3_dial)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            capture("08-directory-details");
            captureOfflineDirectory();
        }
    }

    private static void captureOfflineDirectory() {
        if (!capturesEnabled()) return;
        androidx.test.espresso.Espresso.pressBack();
        awaitDisplayedText("Service fictif 30");
        shell("touch /data/local/tmp/chifaa-e3-final/offline-ready");
        try {
            awaitMarker("offline-ack");
            assertOfflineDirectory();
            capture("09-directory-offline");
        } finally {
            shell("touch /data/local/tmp/chifaa-e3-final/offline-done");
            awaitMarker("offline-restored");
        }
    }

    private static void assertOfflineDirectory() {
        var offline = allOf(withId(R.id.status),withText(org.hamcrest.Matchers.startsWith("Hors ligne")));
        long deadline = SystemClock.uptimeMillis() + 20_000;
        do {
            try {
                onView(offline).check(matches(isDisplayed()));
                return;
            } catch (androidx.test.espresso.NoMatchingViewException pending) {
                onView(withText(R.string.e3_refresh)).perform(click());
                SystemClock.sleep(500);
            }
        } while (SystemClock.uptimeMillis() < deadline);
        onView(offline).check(matches(isDisplayed()));
    }

    private static void awaitMarker(String marker) {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        do {
            String path = "/data/local/tmp/chifaa-e3-final/" + marker;
            if (path.equals(shell("ls " + path).trim())) return;
            SystemClock.sleep(200);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing emulator network marker " + marker);
    }

    private static String shell(String command) {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try (var input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))) {
            var result = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int read;
            while ((read = input.read(buffer)) != -1) result.write(buffer, 0, read);
            return result.toString("UTF-8");
        } catch (java.io.IOException error) { throw new AssertionError(command,error); }
    }

    @Test public void localeSelectorRendersArabicHome() {
        try (var screen = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(R.string.e3_language)).perform(click());
            onView(withText("العربية")).perform(click());
            awaitDisplayedText("أهلاً بك في شفاء");
            capture("16-arabic-rtl");
            onView(withText(R.string.e3_language)).perform(click());
            onView(withText("Français")).perform(click());
            awaitDisplayed(R.string.e3_welcome);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags("fr")));
        }
    }

    private static void capture(String name) {
        if (!capturesEnabled()) return;
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        String path = "/data/local/tmp/chifaa-e3-final/" + name + ".png";
        shell("screencap -p " + path);
        if (!path.equals(shell("ls " + path).trim())) throw new AssertionError("Screenshot unavailable: " + name);
    }

    private static boolean capturesEnabled() {
        return "true".equals(InstrumentationRegistry.getArguments().getString("chifaaCapture"));
    }

    private static void awaitDisplayedText(String label) {
        awaitDisplayed(allOf(withText(label),isDescendantOfA(withId(R.id.list))),label);
    }

    private static void awaitDisplayed(int label) {
        awaitDisplayed(withText(label),Integer.toString(label));
    }

    private static void awaitDisplayed(org.hamcrest.Matcher<android.view.View> view, String label) {
        long expiry = SystemClock.uptimeMillis() + 20_000;
        Throwable last = null;
        do {
            try {
                onView(allOf(view,isDisplayed())).check(matches(isDisplayed()));
                return;
            } catch (AssertionError | androidx.test.espresso.NoMatchingViewException pending) {
                last = pending;
                SystemClock.sleep(200);
            }
        } while (SystemClock.uptimeMillis() < expiry);
        throw new AssertionError("Expected visible view: " + label,last);
    }
}
