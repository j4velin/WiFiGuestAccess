package de.j4velin.gastzugang;

public class WiFiData {
    public String ssid, key;
    public int mode, autoDisableTime;
    public boolean enabled, autoDisable, autoDisableNoConnection, protocol, only_web,
            allow_communication;

    public WiFiData setSsid(String ssid) {
        this.ssid = ssid;
        return this;
    }

    public WiFiData setKey(String key) {
        this.key = key;
        return this;
    }

    public WiFiData setMode(int mode) {
        this.mode = mode;
        return this;
    }

    public WiFiData setAutoDisableTime(int autoDisableTime) {
        this.autoDisableTime = autoDisableTime;
        return this;
    }

    public WiFiData setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public WiFiData setAutoDisable(boolean autoDisable) {
        this.autoDisable = autoDisable;
        return this;
    }

    public WiFiData setAutoDisableNoConnection(boolean autoDisableNoConnection) {
        this.autoDisableNoConnection = autoDisableNoConnection;
        return this;
    }

    public WiFiData setProtocol(boolean protocol) {
        this.protocol = protocol;
        return this;
    }

    public WiFiData setOnly_web(boolean only_web) {
        this.only_web = only_web;
        return this;
    }

    public WiFiData setAllow_communication(boolean allow_communication) {
        this.allow_communication = allow_communication;
        return this;
    }

    public WiFiData() {
    }

    public WiFiData(WiFiData original) {
        this.ssid = original.ssid;
        this.key = original.key;
        this.mode = original.mode;
        this.autoDisableTime = original.autoDisableTime;
        this.enabled = original.enabled;
        this.autoDisable = original.autoDisable;
        this.autoDisableNoConnection = original.autoDisableNoConnection;
        this.protocol = original.protocol;
        this.only_web = original.only_web;
        this.allow_communication = original.allow_communication;
    }

    @Override
    public String toString() {
        String m;
        switch (mode) {
            case 2:
                m = "WPA";
                break;
            default:
            case 3:
            case 4:
                m = "WPA2";
                break;
            case 5:
                m = "nopass";
                break;
        }
        return "WIFI:S:" + ssid + ";T:" + m + ";P:" + key + ";" + enabled + ";";
    }
}