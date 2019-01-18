package de.j4velin.gastzugang.version;

import android.text.Html;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;

import de.j4velin.gastzugang.BuildConfig;
import de.j4velin.gastzugang.Logger;
import de.j4velin.gastzugang.Util;
import de.j4velin.gastzugang.WiFiData;

public class FritzOs7 implements FritzOs {

    private final static String KEY_SSID = "[\"wlan:settings/guest_ssid\"]";
    private final static String KEY_PSK = "[\"wlan:settings/guest_pskvalue\"]";

    private final static String KEY_SET_SSID = "ssid";
    private final static String KEY_SET_PSK = "psk";
    private final static String KEY_SET_ALLOW_COMM = "isolated";
    private final static String KEY_SET_ONLY_WEB = "guestGroupAccess";

    @Override
    public WiFiData readConfig(final String FRITZBOX_ADDRESS, final String SID) throws IOException {
        URL url = new URL("http://" + FRITZBOX_ADDRESS + "/wlan/pp_qrcode.lua?sid=" + SID);
        if (BuildConfig.DEBUG) Logger.log("reading from " + url);
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        String line = br.readLine();
        WiFiData currentConfig = new WiFiData();
        while (line != null) {
            if (BuildConfig.DEBUG) Logger.log("  read: " + line);
            if (line.contains(KEY_SSID)) {
                String ssid = Html.fromHtml(getValue(line)).toString();
                if (ssid.endsWith("-OFF")) {
                    ssid = ssid.substring(0, ssid.length() - 4);
                    currentConfig.setEnabled(false);
                } else {
                    currentConfig.setEnabled(true);
                }
                currentConfig.setSsid(ssid);
            } else if (line.contains(KEY_PSK)) {
                currentConfig.setKey(Html.fromHtml(getValue(line)).toString());
            }
            line = br.readLine();
        }
        br.close();
        if (currentConfig.key == null || currentConfig.ssid == null) {
            if (BuildConfig.DEBUG) Logger.log(
                    "can not read ssid/key: ssid=" + currentConfig.ssid + ", key=null? " +
                            (currentConfig.key != null));
            return null;
        } else {
            if (BuildConfig.DEBUG) Logger.log("current config: " + currentConfig);
            return currentConfig;
        }
    }

    @Override
    public boolean setConfig(final String FRITZBOX_ADDRESS, final String SID,
                             final WiFiData newConfig) {
        if (BuildConfig.DEBUG) Logger.log("set net state to: " + newConfig);
        final HashMap<String, String> parameters = new HashMap<>();

        if (!newConfig.enabled) {
            // first, change SSID
            parameters.put("xhr", "1");
            parameters.put(KEY_SET_SSID,
                    newConfig.enabled ? newConfig.ssid : newConfig.ssid + "-OFF");
            parameters.put("apply", "");
            parameters.put("sid", SID);
            parameters.put("lang", "de");
            parameters.put("page", "wGuest");

            if (Util.postData("http://" + FRITZBOX_ADDRESS + "/data.lua", parameters)) {
                // then turn off
                parameters.put("guestAccessActive", "off");
                parameters.put("isEnabled", "0");

                return Util.postData("http://" + FRITZBOX_ADDRESS + "/data.lua", parameters);
            } else {
                return false;
            }
        } else {
            parameters.put("xhr", "1");
            parameters.put("guestAccessActive", "on");
            parameters.put("guestAccessType", "1");
            parameters.put(KEY_SET_SSID, newConfig.ssid);
            parameters.put(KEY_SET_PSK, newConfig.key);
            parameters.put("isEnabled", "1");
            parameters.put(KEY_SET_ALLOW_COMM, newConfig.allow_communication ? "0" : "1");
            parameters.put(KEY_SET_ONLY_WEB, newConfig.only_web ? "1" : "0");
            parameters.put("apply", "");
            parameters.put("sid", SID);
            parameters.put("lang", "de");
            parameters.put("page", "wGuest");

            return Util.postData("http://" + FRITZBOX_ADDRESS + "/data.lua", parameters);
        }
    }

    @Override
    public int getVersion() {
        return 7;
    }

    private static String getValue(String line) {
        String result = line.split(" = ")[1];
        return result.substring(1, result.length() - 3);
    }
}
