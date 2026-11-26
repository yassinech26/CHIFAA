package tn.chifaa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Component
public class PhoneVault {
    private final SecretKeySpec encryption;
    private final SecretKeySpec lookup;
    private final SecureRandom random = new SecureRandom();

    public PhoneVault(@Value("${chifaa.phone-key}") String key, @Value("${chifaa.lookup-key}") String index) {
        byte[] aes = Base64.getDecoder().decode(key);
        byte[] mac = Base64.getDecoder().decode(index);
        if (aes.length != 32 || mac.length != 32 || MessageDigest.isEqual(aes,mac))
            throw new IllegalArgumentException("Two distinct 256-bit keys required");
        encryption = new SecretKeySpec(aes, "AES");
        lookup = new SecretKeySpec(mac, "HmacSHA256");
    }
    String fingerprint(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(lookup);
            return HexFormat.of().formatHex(mac.doFinal(phone.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    public String decrypt(byte[] sealed) {
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,encryption,new GCMParameterSpec(128,sealed,0,12));
            return new String(cipher.doFinal(sealed,12,sealed.length-12),StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    byte[] encrypt(String phone) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryption, new GCMParameterSpec(128,iv));
            byte[] ciphertext = cipher.doFinal(phone.getBytes(StandardCharsets.US_ASCII));
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
}
