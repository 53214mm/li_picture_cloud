package com.li.lipicturecloud.infrastructure.airuntime;

import com.li.lipicturecloud.application.airuntime.CredentialCipher;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.EncryptedCredential;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * AES-256-GCM 凭据加解密。密文格式为 {@code base64(iv):base64(密文+认证标签)}，
 * 每次加密使用全新 12 字节随机 IV；认证失败（密钥错误或密文被篡改）一律解密失败。
 */
public class AesGcmCredentialCipher implements CredentialCipher {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec masterKey;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCredentialCipher(String masterKeyMaterial) {
        this.masterKey = new SecretKeySpec(decodeKey(masterKeyMaterial), "AES");
    }

    @Override
    public EncryptedCredential encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String cipherText = Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(sealed);
            return new EncryptedCredential(alphanumericTail(plaintext), cipherText,
                    CredentialVault.ALGORITHM_AES_GCM_V1);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("credential encryption failed", error);
        }
    }

    @Override
    public String decrypt(CredentialVault credential) {
        Objects.requireNonNull(credential, "credential");
        if (!CredentialVault.ALGORITHM_AES_GCM_V1.equals(credential.algorithm())) {
            throw new IllegalArgumentException("unsupported credential algorithm: "
                    + credential.algorithm());
        }
        String[] parts = credential.cipherText().split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("malformed credential ciphertext");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] sealed = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (AEADBadTagException tampered) {
            throw new IllegalStateException("credential ciphertext failed authentication", tampered);
        } catch (IllegalArgumentException | GeneralSecurityException error) {
            throw new IllegalStateException("credential decryption failed", error);
        }
    }

    /**
     * 取明文末尾最多 4 个字母数字字符作为展示尾号；不足时左侧补 0，避免泄露明文结构。
     */
    static String alphanumericTail(String plaintext) {
        StringBuilder tail = new StringBuilder();
        for (int i = plaintext.length() - 1; i >= 0 && tail.length() < 4; i--) {
            char ch = plaintext.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                tail.append(ch);
            }
        }
        tail.reverse();
        while (tail.length() < 4) {
            tail.insert(0, '0');
        }
        return tail.toString();
    }

    static byte[] decodeKey(String material) {
        Objects.requireNonNull(material, "masterKeyMaterial");
        String trimmed = material.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("master key material must not be blank");
        }
        byte[] key;
        if (trimmed.matches("[0-9a-fA-F]{64}")) {
            key = HexFormat.of().parseHex(trimmed);
        } else {
            key = Base64.getDecoder().decode(trimmed);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalArgumentException("master key must decode to exactly "
                    + KEY_BYTES + " bytes");
        }
        return key;
    }
}
