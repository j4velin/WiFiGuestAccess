package de.j4velin.gastzugang.version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;

import de.j4velin.gastzugang.BuildConfig;
import de.j4velin.gastzugang.Logger;
import de.j4velin.gastzugang.Util;
import de.j4velin.gastzugang.WiFiData;

public class FritzOs6 implements FritzOs {

    private final static String KEY_ACTIVATE = "activate_guest_access";
    private final static String KEY_SSID = "guest_ssid";
    private final static String KEY_SEC_MODE = "sec_mode";
    private final static String KEY_SEC_DEPRECATED = "wlan_security";
    private final static String KEY_SEC_MODE_DEPRECATED = "wpa_modus";
    private final static String KEY_PASSWORD = "wpa_key";
    private final static String KEY_AUTODISABLE = "down_time_activ";
    private final static String KEY_AUTODISABLE_NOCON = "disconnect_guest_access";
    private final static String KEY_AUTODISABLE_TIME = "down_time_value";
    private final static String KEY_PROTOCOL = "push_service";
    private final static String KEY_ALLOW_COMMUNICATION = "user_isolation";
    private final static String KEY_ONLY_WEB = "group_access";

    private static String addNameTag(final String key) {
        return "name=\"" + key + "\"";
    }

    @Override
    public WiFiData readConfig(final String FRITZBOX_ADDRESS, final String SID) throws IOException {
        URL url = new URL("http://" + FRITZBOX_ADDRESS + "/wlan/guest_access.lua?sid=" + SID);
        if (BuildConfig.DEBUG) Logger.log("reading from " + url);
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        String line = br.readLine();
        WiFiData currentConfig = new WiFiData();
        boolean modeLine = false;
        boolean timeLine = false;
        while (line != null) {
            // if (BuildConfig.DEBUG) Logger.log("  read: " + line);
            if (line.contains(addNameTag(KEY_ACTIVATE))) {
                currentConfig.setEnabled(line.contains("checked"));
            } else if (line.contains(addNameTag(KEY_SSID))) {
                currentConfig.setSsid(line.substring(line.indexOf("value=\"") + 7,
                        line.indexOf("\"", line.indexOf("value=\"") + 7)));
            } else if (line.contains(addNameTag(KEY_SEC_MODE))) {
                modeLine = true;
            } else if (modeLine && line.contains("<option value=") &&
                    line.contains("selected=\"selected\"")) {
                currentConfig.setMode(Integer.parseInt(line.substring(line.indexOf("\"") + 1,
                        line.indexOf("\"", line.indexOf("\"") + 1))));
            } else if (modeLine && line.contains("</select>")) {
                modeLine = false;
            } else if (line.contains(addNameTag(KEY_PASSWORD))) {
                currentConfig.setKey(line.substring(line.indexOf("value=\"") + 7,
                        line.indexOf("\"", line.indexOf("value=\"") + 7)));
            } else if (line.contains(addNameTag(KEY_AUTODISABLE))) {
                currentConfig.setAutoDisable(line.contains("checked"));
            } else if (line.contains(addNameTag(KEY_AUTODISABLE_NOCON))) {
                currentConfig.setAutoDisableNoConnection(line.contains("checked"));
            } else if (line.contains(addNameTag(KEY_AUTODISABLE_TIME))) {
                timeLine = true;
            } else if (timeLine && line.contains("<option value=") &&
                    line.contains("selected=\"selected\"")) {
                currentConfig.setAutoDisableTime(Integer.parseInt(
                        line.substring(line.indexOf("\"") + 1,
                                line.indexOf("\"", line.indexOf("\"") + 1))));
            } else if (timeLine && line.contains("</select>")) {
                timeLine = false;
            } else if (line.contains(addNameTag(KEY_PROTOCOL))) {
                currentConfig.setProtocol(line.contains("checked"));
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
        final HashMap<String, String> parameters = new HashMap<>(8);
        parameters.put("apply", "");
        parameters.put("btnSave", "");

        if (newConfig.enabled) parameters.put(KEY_ACTIVATE, "on");
        if (newConfig.only_web) parameters.put(KEY_ONLY_WEB, "on");
        if (newConfig.allow_communication) parameters.put(KEY_ALLOW_COMMUNICATION, "on");
        if (newConfig.protocol) parameters.put(KEY_PROTOCOL, "on");

        if (newConfig.autoDisable) {
            parameters.put(KEY_AUTODISABLE, "on");
            parameters.put(KEY_AUTODISABLE_TIME, String.valueOf(newConfig.autoDisableTime));
            if (newConfig.autoDisableNoConnection) parameters.put(KEY_AUTODISABLE_NOCON, "on");
        }
        parameters.put(KEY_SSID, newConfig.ssid);
        int sec_mode = newConfig.mode;
        if (sec_mode == 5) {
            // no encryption
            parameters.put(KEY_SEC_DEPRECATED, "1");
        } else {
            parameters.put(KEY_SEC_DEPRECATED, "0");
            parameters.put(KEY_SEC_MODE_DEPRECATED, String.valueOf(sec_mode));
            parameters.put(KEY_PASSWORD, newConfig.key);
        }
        parameters.put(KEY_SEC_MODE, String.valueOf(sec_mode));

        return Util.postData("http://" + FRITZBOX_ADDRESS + "/wlan/guest_access.lua?sid=" + SID,
                parameters);
    }

    @Override
    public int getVersion() {
        return 6;
    }
}
