package com.android.chrysaoralike;

import android.util.Base64;
import android.util.Log;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HttpCrypto {
    private static final String TAG = "HttpCrypto";
    private static final byte[] IV = new byte[]{ '"', (byte)0x85, 'O', (byte)0xa6, 'f', 'y', (byte)0x07, (byte)0xa6, (byte)0xae, '[', (byte)0x8b, 0x1e, ':', (byte)0x05, (byte)0x9b, (byte)0xbf };
    private static final byte[] HARDCODED_KEY1 = new byte[]{ 'V', '@', '~', 'D', (byte)0xea, 0x02, (byte)0xfd, 0x01, 0x07, (byte)0x99, 'x', (byte)0xa4, '`', (byte)0x93, '8', 'X', (byte)0xf3, 'Y', (byte)0xcf, (byte)0x90, (byte)0x87, '@', (byte)0xd7, 'g', (byte)0xef, (byte)0xae, (byte)0x91, 0x19, (byte)0xcf, 0x11, 'X', 'J' };
    private static final byte[] SALT = new byte[]{ (byte)0xb6, 0x27, (byte)0xdb, '!', 0x5c, '}', '5', (byte)0xe4 };
    private static final IvParameterSpec IV_SPEC = new IvParameterSpec(IV);
    private static int errorCounter = 0;

    public static byte[] aesEncryptOutbound(byte[] data, byte[] key) {
        if (data == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), IV_SPEC);
            return cipher.doFinal(data);
        } catch (Exception e) {
            Log.e(TAG, "AES encrypt error", e);
            return null;
        }
    }

    public static byte[] defaultAesEncrypt(byte[] data) {
        return aesEncryptOutbound(data, HARDCODED_KEY1);
    }

    public static String serverResponseDecrypt(byte[] encryptedData, String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] saltAndToken = new byte[SALT.length + token.getBytes("UTF-8").length];
            System.arraycopy(SALT, 0, saltAndToken, 0, SALT.length);
            System.arraycopy(token.getBytes("UTF-8"), 0, saltAndToken, SALT.length, token.getBytes("UTF-8").length);
            md.update(saltAndToken);
            byte[] digest = md.digest();
            byte[] key = new byte[digest.length * 2];
            System.arraycopy(digest, 0, key, 0, digest.length);
            System.arraycopy(digest, 0, key, digest.length, digest.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), IV_SPEC);
            byte[] decrypted = cipher.doFinal(encryptedData);
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Server response decrypt error", e);
            return null;
        }
    }

    public static String makeSessionId1(String token) {
        byte[] encrypted = aesEncryptOutbound(token.getBytes(), HARDCODED_KEY1);
        return encrypted != null ? Base64.encodeToString(encrypted, Base64.NO_WRAP) : "";
    }

    public static String makeSessionId2(byte[] sessionKey) {
        byte[] encrypted = aesEncryptOutbound(sessionKey, HARDCODED_KEY1);
        return encrypted != null ? Base64.encodeToString(encrypted, Base64.NO_WRAP) : "";
    }

    public static byte[] generateSessionKey() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] key = new byte[32];
        random.nextBytes(key);
        return key;
    }

    public static byte[] gzipCompress(byte[] data) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(baos);
            gzos.write(data);
            gzos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "GZIP error", e);
            return data;
        }
    }
}