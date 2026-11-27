package com.example.chifaaapp;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.*;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertTrue;

public final class DeviceTls {
    private DeviceTls() {}

    public static OkHttpClient.Builder client(JsonObject fixture) throws Exception {
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
        return new OkHttpClient.Builder().sslSocketFactory(tls.getSocketFactory(),manager);
    }
}
