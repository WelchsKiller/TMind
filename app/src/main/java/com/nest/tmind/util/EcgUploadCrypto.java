package com.nest.tmind.util;

import android.util.Base64;

import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * ECG 업로드용 Hybrid Encryption.
 * CEK: AES-256-GCM (IV 12byte, Auth Tag 16byte)
 * CEK 래핑: RSA-OAEP-256 (SHA-256, RSA 3072bit)
 * 인코딩: Base64URL
 */
public final class EcgUploadCrypto {

    private static final Gson GSON = new Gson();
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String AES_TRANSFORM = "AES/GCM/NoPadding";
    private static final String RSA_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private EcgUploadCrypto() {
    }

    public static final class EncryptedPayload {
        public final String keyId;
        public final String encryptedCek;
        public final String iv;
        public final String ciphertext;
        public final String authTag;

        public EncryptedPayload(String keyId, String encryptedCek, String iv,
                                String ciphertext, String authTag) {
            this.keyId = keyId;
            this.encryptedCek = encryptedCek;
            this.iv = iv;
            this.ciphertext = ciphertext;
            this.authTag = authTag;
        }
    }

    /**
     * 공개키만 표준 Base64(X.509 SubjectPublicKeyInfo DER)이고, 업로드 페이로드의
     * encryptedCek/iv/ciphertext/authTag 는 Base64URL 이다. 디코더가 다르므로 섞지 않는다.
     */
    public static PublicKey parseRsaPublicKey(String pemOrBase64) throws Exception {
        if (pemOrBase64 == null || pemOrBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("empty public key");
        }
        String normalized = pemOrBase64.trim()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "")
                // 표준 Base64 에는 '-','_' 가 없어서, URL-safe 로 내려와도 안전하게 되돌린다.
                .replace('-', '+')
                .replace('_', '/');
        byte[] der = Base64.decode(normalized, Base64.DEFAULT);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    public static EncryptedPayload encrypt(byte[] plaintext, PublicKey publicKey, String keyId)
            throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey cek = kg.generateKey();

        byte[] iv = new byte[GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);

        Cipher aes = Cipher.getInstance(AES_TRANSFORM);
        aes.init(Cipher.ENCRYPT_MODE, cek, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encrypted = aes.doFinal(plaintext);

        int tagLen = 16;
        int cipherLen = encrypted.length - tagLen;
        byte[] ciphertext = new byte[cipherLen];
        byte[] authTag = new byte[tagLen];
        System.arraycopy(encrypted, 0, ciphertext, 0, cipherLen);
        System.arraycopy(encrypted, cipherLen, authTag, 0, tagLen);

        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        Cipher rsa = Cipher.getInstance(RSA_TRANSFORM);
        rsa.init(Cipher.ENCRYPT_MODE, publicKey, oaep);
        byte[] wrappedCek = rsa.doFinal(cek.getEncoded());

        return new EncryptedPayload(
                keyId,
                base64Url(wrappedCek),
                base64Url(iv),
                base64Url(ciphertext),
                base64Url(authTag)
        );
    }

    public static byte[] toJsonBytes(EncryptedPayload payload) {
        return GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
    }

    private static String base64Url(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
