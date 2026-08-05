package com.nest.tmind.util;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM 암·복호화 (Android Keystore).
 * 로컬 저장 및 전송 페이로드 보호에 사용.
 */
public final class AesCrypto {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "tmind_aes256_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;

    private AesCrypto() {
    }

    public static synchronized void ensureKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return;

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build();
        kg.init(spec);
        kg.generateKey();
    }

    private static SecretKey getKey() throws Exception {
        ensureKey();
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    /** 평문 → Base64(IV + ciphertext+tag) */
    public static String encryptToBase64(String plain) throws Exception {
        if (plain == null) return null;
        byte[] input = plain.getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(input);
        ByteBuffer buf = ByteBuffer.allocate(iv.length + encrypted.length);
        buf.put(iv);
        buf.put(encrypted);
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP);
    }

    public static String decryptFromBase64(String encoded) throws Exception {
        if (encoded == null || encoded.isEmpty()) return encoded;
        byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
        if (all.length <= IV_LEN) throw new IllegalArgumentException("cipher too short");
        byte[] iv = new byte[IV_LEN];
        System.arraycopy(all, 0, iv, 0, IV_LEN);
        byte[] cipherBytes = new byte[all.length - IV_LEN];
        System.arraycopy(all, IV_LEN, cipherBytes, 0, cipherBytes.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(cipherBytes);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** SharedPreferences 등에서 안전하게 읽기 (평문 레거시 호환) */
    public static String decryptOrPlain(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        try {
            return decryptFromBase64(stored);
        } catch (Exception e) {
            return stored;
        }
    }

    public static String encryptSafe(Context ctx, String plain) {
        try {
            return encryptToBase64(plain);
        } catch (Exception e) {
            return plain;
        }
    }
}
