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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.android.encode.QRCodeEncoder;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainFragment extends Fragment {

    private boolean currently_enabled = false;
    static String SID;
    static String FRITZBOX_PW, FRITZBOX_USER, FRITZBOX_ADDRESS;

    private WiFiData currentConfig;

    private EditText ssid, key;
    private Button button;
    private ImageView image;
    private View scanToConnect;

    private ProgressDialog pg;

    private boolean wifiCurrentlyConnected;
    private Dialog loginCredentials;

    private int display_width = 600;

    private final static String ALL_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (BuildConfig.DEBUG)
                android.util.Log.d(MainActivity.TAG, "WiFi change: " + intent.getAction());
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (!wifiCurrentlyConnected && info.isConnected()) {
                    setError(null);
                    readState();
                } else if (!info.isConnected()) {
                    setError(context.getString(R.string.not_connected));
                }
                wifiCurrentlyConnected = info.isConnected();
            }
        }
    };

    private void setError(final String msg) {
        TextView error = (TextView) getView().findViewById(R.id.error);
        if (msg == null) {
            error.setVisibility(View.GONE);
        } else {
            error.setText(msg);
            error.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        WifiManager wm = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
        wifiCurrentlyConnected = wm.getWifiState() == WifiManager.WIFI_STATE_ENABLED &&
                wm.getConnectionInfo() != null;
        getActivity().registerReceiver(receiver,
                new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        if (wifiCurrentlyConnected) {
            if (BuildConfig.DEBUG)
                android.util.Log.d(MainActivity.TAG, "WiFi is currently connected -> readState");
            setError(null);
            readState();
        } else {
            setError(getActivity().getString(R.string.not_connected));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(receiver);
        pg = null;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        FRITZBOX_USER = Util.decrypt(prefs.getString("fb_user", null), getActivity());
        FRITZBOX_PW = Util.decrypt(prefs.getString("fb_pw", null), getActivity());
        FRITZBOX_ADDRESS = prefs.getString("address", "fritz.box");

        final View v = inflater.inflate(R.layout.content_main, null);
        ssid = (EditText) v.findViewById(R.id.ssid);
        key = (EditText) v.findViewById(R.id.key);
        button = (Button) v.findViewById(R.id.button);
        image = (ImageView) v.findViewById(R.id.image);
        scanToConnect = v.findViewById(R.id.scantext);

        v.findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (currentConfig == null) {
                    readState();
                } else {
                    changeWiFi(!currently_enabled);
                }
            }
        });

        v.findViewById(R.id.error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });

        if (MainActivity.PRO_VERSION) {
            v.findViewById(R.id.nag).setVisibility(View.GONE);
        } else {
            v.findViewById(R.id.nag).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    ((MainActivity) getActivity()).purchase(MainFragment.this);
                }
            });
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        display_width = (int) (metrics.widthPixels * 0.9f);

        return v;
    }

    void purchased() {
        getView().findViewById(R.id.nag).setVisibility(View.GONE);
        if (BuildConfig.DEBUG) android.util.Log.d(MainActivity.TAG, "purchased");
    }

    private void askForLogin() {
        if (loginCredentials != null && loginCredentials.isShowing()) loginCredentials.dismiss();
        final View v = getActivity().getLayoutInflater().inflate(R.layout.password_dialog, null);
        final EditText pw = (EditText) v.findViewById(R.id.pw);
        final EditText user = (EditText) v.findViewById(R.id.user);
        final CheckBox save = (CheckBox) v.findViewById(R.id.save);
        save.setChecked(
                PreferenceManager.getDefaultSharedPreferences(getActivity()).contains("fb_pw"));
        pw.setText(FRITZBOX_PW);
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        user.setText(FRITZBOX_USER);
        loginCredentials = new AlertDialog.Builder(getActivity()).setView(v)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialogInterface, int i) {
                        FRITZBOX_PW = pw.getText().toString();
                        FRITZBOX_USER = user.getText().toString();
                        if (save.isChecked()) {
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                                    .putString("fb_pw", Util.encrypt(FRITZBOX_PW, getActivity()))
                                    .putString("fb_user",
                                            Util.encrypt(FRITZBOX_USER, getActivity())).apply();
                        } else {
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                                    .remove("fb_pw").remove("fb_user").apply();
                        }
                        dialogInterface.dismiss();
                        readState();
                    }
                }).setNegativeButton(R.string.help, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        getActivity().startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("http://j4velin.de/faq/index.php?app=gz"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }).create();
        loginCredentials.show();
    }

    private void blocked(final int time) {
        if (BuildConfig.DEBUG) android.util.Log.d(MainActivity.TAG, "blocked " + time);
        final Handler h = new Handler();
        if (pg != null && pg.isShowing()) pg.dismiss();
        pg = new ProgressDialog(getActivity());
        pg.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(final DialogInterface dialogInterface) {
                getActivity().finish();
            }
        });
        pg.show();
        h.post(new Runnable() {
            int waited = 0;

            @Override
            public void run() {
                pg.setMessage(getString(R.string.blocked, time - waited));
                waited++;
                if (waited < time) {
                    h.postDelayed(this, 1000);
                } else {
                    pg.dismiss();
                    askForLogin();
                }
            }
        });
    }

    private void updateState() {
        if (BuildConfig.DEBUG) android.util.Log.d(MainActivity.TAG, "updateState " + currentConfig);
        image.setVisibility(currently_enabled ? View.VISIBLE : View.GONE);
        scanToConnect.setVisibility(currently_enabled ? View.VISIBLE : View.GONE);
        ssid.setEnabled(!currently_enabled);
        key.setEnabled(!currently_enabled);
        if (currentConfig != null) {
            if (MainActivity.PRO_VERSION) {
                ssid.setText(currentConfig.ssid);
                key.setText(currentConfig.key);
            }
            if (currently_enabled) {
                setQrCode(currentConfig.toString());
                Notification.Builder b =
                        new Notification.Builder(getActivity()).setContentTitle("Guest-WiFi active")
                                .setContentText(currentConfig.ssid + " - " + currentConfig.key)
                                .setSmallIcon(R.drawable.ic_icon).setContentIntent(PendingIntent
                                .getActivity(getActivity(), 0,
                                        new Intent(getActivity(), MainActivity.class)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                Notification n;
                if (Build.VERSION.SDK_INT < 16) {
                    n = b.getNotification();
                } else {
                    n = b.build();
                }
                ((NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE))
                        .notify(1, n);
            } else {
                ((NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE))
                        .cancel(1);
            }
            button.setText(currently_enabled ? R.string.disable : R.string.enable);
        } else {
            button.setText(R.string.read_config);
        }
    }

    private void setQrCode(final String data) {
        try {
            QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(data, BarcodeFormat.QR_CODE, 800);
            Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();
            ((ImageView) getView().findViewById(R.id.image)).setImageBitmap(
                    bitmap.createScaledBitmap(bitmap, display_width, display_width, true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void changeWiFi(final boolean enable) {
        if (BuildConfig.DEBUG) Log.d(MainActivity.TAG, "change wifi");
        pg = new ProgressDialog(getActivity());
        pg.setMessage(getString(R.string.please_wait));
        pg.show();
        final Handler h = new Handler();
        if (!MainActivity.PRO_VERSION) {
            this.ssid.setText("Hotspot");
            this.key.setText("swordfish");
        }
        final String wifi_ssid = this.ssid.getText().toString();
        final String wifi_key = this.key.getText().toString();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (SID == null) {
                        Parser.LoginEntry login = getLogin();
                        if (login == null) return;
                        SID = login.sid;
                    }
                    final SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(getActivity());
                    final HashMap<String, String> parameters = new HashMap<>(8);
                    parameters.put("apply", "");
                    parameters.put("btnSave", "");
                    if (enable) {
                        String key;
                        if (prefs.getBoolean("random_key", false)) {
                            key = getRandomKey(Long.parseLong(SID.replaceAll("\\D+", "")) +
                                    System.currentTimeMillis());
                        } else {
                            key = wifi_key;
                            while (key.length() < 8) {
                                key += Math.random() * 10;
                            }
                        }
                        if (BuildConfig.DEBUG) {
                            Log.d(MainActivity.TAG, "enabling network " + wifi_ssid + " " + key);
                        }
                        parameters.put("activate_guest_access", "on");
                        parameters.put("guest_ssid", wifi_ssid);
                        parameters
                                .put("sec_mode", String.valueOf(prefs.getInt("wifi_security", 3)));
                        parameters.put("wpa_key", key);
                        if (prefs.getBoolean("wifi_limited_access", true))
                            parameters.put("group_access", "on");
                        if (prefs.getBoolean("wifi_communicate", false))
                            parameters.put("user_isolation", "on");
                    } else if (BuildConfig.DEBUG) {
                        Log.d(MainActivity.TAG, "disabling network");
                    }
                    // automatisch deaktivieren:
                    //                    <input type="checkbox" id="uiViewDownTimeActiv" name="down_time_activ" onclick="onDownTimerActiv()" >
                    //                    <label id="uiViewDownTimeActivLabel" for="uiViewDownTimeActiv">automatisch deaktivieren nach</label>
                    //                    <span id="uiViewDownTimeBox">
                    //                    <select id="uiViewDownTime" name="down_time_value" size="1">
                    //                    <option value="15" >15 Minuten</option>
                    //                    <option value="30" selected="selected">30 Minuten</option>
                    //                    <option value="45" >45 Minuten</option>
                    //                    <option value="60" >60 Minuten</option>
                    //                    <option value="90" >90 Minuten</option>
                    //                    <option value="120" > 2 Stunden</option>
                    //                    <option value="180" > 3 Stunden</option>
                    //                    <option value="240" > 4 Stunden</option>
                    //                    <option value="300" > 5 Stunden</option>
                    //                    <option value="360" > 6 Stunden</option>
                    //                    <option value="480" > 8 Stunden</option>
                    //                    <option value="600" >10 Stunden</option>
                    //                    <option value="720" >12 Stunden</option>
                    //                    <option value="900" >15 Stunden</option>
                    //                    <option value="1080" >18 Stunden</option>
                    //                    <option value="1260" >21 Stunden</option>

                    if (!postData("http://fritz.box/wlan/guest_access.lua?sid=" + SID,
                            parameters)) {
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                showErrorDialog(getString(R.string.guest_settings_not_found));
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            readState();
                        }
                    }, 1000);
                }
            }
        }).start();
    }


    private boolean postData(final String requestURL, final HashMap<String, String> postDataParams) {
        URL url;
        if (BuildConfig.DEBUG) android.util.Log.d(MainActivity.TAG, "post data to: " + requestURL);
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
            if (BuildConfig.DEBUG)
                android.util.Log.d(MainActivity.TAG, "response code: " + responseCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return responseCode == HttpURLConnection.HTTP_OK;
    }

    private String getPostDataString(final HashMap<String, String> params) throws
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

    private void showErrorDialog(final String msg) {
        new AlertDialog.Builder(getActivity()).setMessage(msg)
                .setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                }).setNegativeButton(R.string.help, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                getActivity().startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://j4velin.de/faq/index.php?app=gz"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }).create().show();
    }

    private Parser.LoginEntry getLogin() {
        String url = FRITZBOX_USER != null && FRITZBOX_USER.length() > 0 ?
                "http://fritz.box/login_sid.lua?username=" + FRITZBOX_USER :
                "http://fritz.box/login_sid.lua";
        try {
            return getLogin(new URL(url));
        } catch (final Exception e) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (e instanceof UnknownHostException) {
                        showErrorDialog(getString(R.string.error_no_fritzbox));
                    } else if (e instanceof FileNotFoundException) {
                        showErrorDialog(getString(R.string.error_unsupported_version));
                    } else {
                        showErrorDialog(
                                "Error: " + e.getClass().getName() + " - " + e.getMessage());
                    }

                }
            });
            e.printStackTrace();
            return null;
        }
    }

    private Parser.LoginEntry getLogin(final URL url) throws IOException, XmlPullParserException,
            NoSuchAlgorithmException {
        if (BuildConfig.DEBUG) android.util.Log.d(MainActivity.TAG, "getting login from " + url);
        Parser p = new Parser();
        InputStream in = url.openStream();
        List<Parser.Entry> entries = p.parse(in);
        in.close();
        if (BuildConfig.DEBUG)
            android.util.Log.d(MainActivity.TAG, "read entries: " + entries.size());
        Parser.LoginEntry login = null;
        for (int i = 0; i < entries.size() && login == null; i++) {
            if (entries.get(i) instanceof Parser.LoginEntry) {
                login = (Parser.LoginEntry) entries.get(0);
            }
        }
        if (BuildConfig.DEBUG) android.util.Log.d(MainActivity.TAG, "login: " + login);
        if (login.sid.equals("0000000000000000")) {
            if (url.toString().contains("response") || FRITZBOX_PW == null ||
                    FRITZBOX_PW.length() < 1) return login;
            String user = FRITZBOX_USER != null && FRITZBOX_USER.length() > 0 ?
                    "?username=" + FRITZBOX_USER + "&" : "?";
            return getLogin(new URL("http://fritz.box/login_sid.lua" + user + "response=" +
                    login.challenge + "-" +
                    Util.md5(login.challenge + "-" + FRITZBOX_PW)));
        } else {
            return login;
        }
    }

    private void readState() {
        if (BuildConfig.DEBUG) android.util.Log.d(MainActivity.TAG, "readstate");
        if (pg != null && pg.isShowing()) pg.dismiss();
        pg = new ProgressDialog(getActivity());
        pg.setTitle(R.string.reading_config);
        pg.setMessage(getString(R.string.please_wait));
        pg.setCancelable(false);
        pg.show();
        final Handler h = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (BuildConfig.DEBUG) Log.d(MainActivity.TAG, "reading current state");
                    if (SID == null) {
                        final Parser.LoginEntry login = getLogin();
                        if (login == null) return;
                        if (login.blockTime > 0) {
                            h.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    blocked(login.blockTime);
                                }
                            }, 500);
                            return;
                        } else if (login.sid.equals("0000000000000000")) {
                            if (BuildConfig.DEBUG)
                                Log.d(MainActivity.TAG, "readstate -> askforlogin");
                            h.post(new Runnable() {
                                @Override
                                public void run() {
                                    askForLogin();
                                }
                            });
                            return;
                        } else {
                            SID = login.sid;
                        }
                    }

                    URL url = new URL("http://fritz.box/wlan/guest_access.lua?sid=" + SID);
                    BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
                    String line = br.readLine();
                    String ssid = null, key = null;
                    int mode = -1;
                    boolean modeLine = false;
                    boolean guest_wifi_page = false;
                    while (line != null) {
                        if (line.contains("name=\"activate_guest_access\"")) {
                            guest_wifi_page = true;
                            currently_enabled = line.contains("checked");
                            if (BuildConfig.DEBUG)
                                Log.d(MainActivity.TAG, "current state: " + currently_enabled);
                        } else if (line.contains("name=\"guest_ssid\"")) {
                            ssid = line.substring(line.indexOf("value=\"") + 7,
                                    line.indexOf("\"", line.indexOf("value=\"") + 7));
                            if (BuildConfig.DEBUG) Log.d(MainActivity.TAG, "current ssid: " + ssid);
                        } else if (line.contains("name=\"sec_mode\"")) {
                            modeLine = true;
                        } else if (modeLine && line.contains("<option value=") &&
                                line.contains("selected=\"selected\"")) {
                            mode = Integer.parseInt(line.substring(line.indexOf("\"") + 1,
                                    line.indexOf("\"", line.indexOf("\"") + 1)));
                            if (BuildConfig.DEBUG) Log.d(MainActivity.TAG, "current mode: " + mode);
                        } else if (line.contains("name=\"wpa_key\"")) {
                            key = line.substring(line.indexOf("value=\"") + 7,
                                    line.indexOf("\"", line.indexOf("value=\"") + 7));
                            if (BuildConfig.DEBUG) Log.d(MainActivity.TAG, "current key: " + key);
                            break;
                        }
                        line = br.readLine();
                    }
                    br.close();
                    if (!guest_wifi_page) SID = null;
                    currentConfig = new WiFiData(Html.fromHtml(ssid).toString(),
                            Html.fromHtml(key).toString(), mode);

                    if (BuildConfig.DEBUG)
                        Log.d(MainActivity.TAG, "current config: " + currentConfig);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (pg.isShowing()) {
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                if (SID != null) {
                                    updateState();
                                } else {
                                    askForLogin();
                                }
                                pg.dismiss();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private String getRandomKey(long seed) {
        Random r = new Random(seed);
        char[] re = new char[10];
        int max = ALL_CHARS.length();
        for (int i = 0; i < 10; i++) {
            re[i] = ALL_CHARS.charAt(r.nextInt(max));
        }
        return String.valueOf(re);
    }

    private class WiFiData {
        private final String ssid, key;
        private final int mode;

        private WiFiData(final String ssid, final String key, final int mode) {
            this.ssid = ssid;
            this.key = key;
            this.mode = mode;
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
            return "WIFI:S:" + ssid + ";T:" + m + ";P:" + key + ";;";
        }
    }

}
