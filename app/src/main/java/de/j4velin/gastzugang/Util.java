/*
 * Copyright 2015 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.j4velin.gastzugang;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import de.j4velin.gastzugang.version.FritzOs;
import de.j4velin.gastzugang.version.FritzOs6;
import de.j4velin.gastzugang.version.FritzOs7;

public abstract class Util {

    static String encrypt(final String value, final Context c) {
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

    static String decrypt(final String value, final Context c) {
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

    static String md5(final String string) throws NoSuchAlgorithmException,
            UnsupportedEncodingException {
        byte[] digest = MessageDigest.getInstance("MD5")
                .digest((string).getBytes(Charset.forName("utf-16le")));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }


    public static boolean postData(final String requestURL,
                                   final HashMap<String, String> postDataParams) {
        URL url;
        if (BuildConfig.DEBUG)
            Logger.log("post data to: " + requestURL + " --> " + postDataParams.entrySet());
        int responseCode = -1;
        try {
            url = new URL(requestURL);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(15000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(getPostDataString(postDataParams));

            writer.flush();
            writer.close();
            os.close();
            responseCode = conn.getResponseCode();
            if (BuildConfig.DEBUG) Logger.log("response code: " + responseCode);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
        }
        return responseCode == HttpURLConnection.HTTP_OK;
    }

    public static String getPostDataString(final HashMap<String, String> params) throws
            UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) first = false;
            else result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    public static FritzOs fromVersion(int v) {
        switch (v) {
            case 6:
                return new FritzOs6();
            case 7:
                return new FritzOs7();
            default:
                return null;
        }
    }

}
