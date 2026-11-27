package com.example.chifaaapp.patient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/** Optional JVM check using the Android client's real Retrofit configuration and a disposable server. */
public class LivePatientApiTest {
    @Test public void connectAndUsePersonalHealthRecord() throws Exception {
        String inbox = System.getenv("CHIFAA_TEST_OTP_DIR");
        assumeNotNull(inbox);
        var repository = PatientRepository.INSTANCE;
        var api = repository.api;
        var form = new Models.Register();
        form.phone = System.getenv("CHIFAA_TEST_PHONE");
        assertNotNull(form.phone);
        form.profile = new Models.Profile("18–29", "TN-11", "French");
        form.service = form.health = form.adult = true;
        var challenge = api.register(form).execute();
        assertEquals(200, challenge.code());
        String id = challenge.body().challengeId;
        Path sms = Path.of(inbox, id);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
        while (!Files.exists(sms) && System.nanoTime() < deadline) Thread.sleep(250);
        var verified = api.verify(new Models.Verify(id, Files.readString(sms).trim())).execute();
        assertEquals(200, verified.code());
        assertTrue(repository.authenticate(verified.body()));
        try {
            assertEquals("TN-11", api.profile().execute().body().governorate);
            var catalog = api.catalog().execute().body();
            assertEquals(7, catalog.carePhases.size());
            assertEquals(11, catalog.sideEffects.size());
            var entry = new Models.Checkin();
            entry.date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
            entry.effects = Collections.singletonList(catalog.sideEffects.get(0));
            var created = api.create(entry).execute();
            assertEquals(200, created.code());
            assertEquals(entry.effects, api.one(created.body().id).execute().body().effects);
            assertTrue(api.change(new Models.Change("Health data", false)).execute().isSuccessful());
            assertEquals(403, api.history().execute().code());
            assertTrue(api.logout().execute().isSuccessful());
            assertEquals(401, api.profile().execute().code());
        } finally {
            repository.close();
        }
    }
}
