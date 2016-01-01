package de.j4velin.gastzugang;


import android.content.Context;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public abstract class Util {

    public static String encrypt(final String value, final Context c) {
        if (value == null) return null;
        try {
            final byte[] bytes = (value + "#correct").getBytes(Charset.forName("UTF-8"));
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(String.valueOf(
                    c.getPackageManager().getPackageInfo(c.getPackageName(), 0).firstInstallTime)
                    .toCharArray()));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
            pbeCipher.init(Cipher.ENCRYPT_MODE, key,
                    new PBEParameterSpec(c.getPackageName().getBytes(Charset.forName("UTF-8")),
                            20));
            return new String(Base64.encode(pbeCipher.doFinal(bytes), Base64.NO_WRAP),
                    Charset.forName("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    public static String decrypt(final String value, final Context c) {
        if (value == null) return null;
        try {
            final byte[] bytes = Base64.decode(value, Base64.DEFAULT);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(String.valueOf(
                    c.getPackageManager().getPackageInfo(c.getPackageName(), 0).firstInstallTime)
                    .toCharArray()));
            Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
            pbeCipher.init(Cipher.DECRYPT_MODE, key,
                    new PBEParameterSpec(c.getPackageName().getBytes(Charset.forName("UTF-8")),
                            20));
            String decrypted = new String(pbeCipher.doFinal(bytes), Charset.forName("UTF-8"));
            if (decrypted.endsWith("#correct")) {
                return decrypted.substring(0, decrypted.lastIndexOf("#correct"));
            } else {
                // decryption failed
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static String md5(final String string) throws NoSuchAlgorithmException,
            UnsupportedEncodingException {
        byte[] digest = MessageDigest.getInstance("MD5")
                .digest((string).getBytes(Charset.forName("utf-16le")));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
