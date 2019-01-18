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

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.android.encode.QRCodeEncoder;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;

import de.j4velin.gastzugang.version.FritzOs;
import de.j4velin.gastzugang.version.FritzOs6;
import de.j4velin.gastzugang.version.FritzOs7;

public class MainFragment extends Fragment {

    public static boolean PRO_VERSION = BuildConfig.FLAVOR.equals("fdroid");
    static String SID;
    static String FRITZBOX_PW, FRITZBOX_USER, FRITZBOX_ADDRESS;

    private FritzOs strategy;

    private WiFiData currentConfig;

    private EditText ssid, key;
    private Button button;
    private ImageView image;
    private View scanToConnect;

    private ProgressDialog pg;

    private boolean wifiCurrentlyConnected;
    private Dialog loginCredentials;

    private static int display_width = 600;

    private final static String ALL_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (BuildConfig.DEBUG) Logger.log("WiFi change: " + intent.getAction());
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            API26Wrapper.createNotificationChannel(getContext());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        WifiManager wm = (WifiManager) getActivity().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        wifiCurrentlyConnected = wm.getWifiState() == WifiManager.WIFI_STATE_ENABLED &&
                wm.getConnectionInfo() != null;
        getActivity().registerReceiver(receiver,
                new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        if (wifiCurrentlyConnected) {
            if (BuildConfig.DEBUG) Logger.log("WiFi is currently connected -> readState");
            setError(null);
            final SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            int version = prefs.getInt("version", -1);
            strategy = Util.fromVersion(version);
            if (strategy == null) {
                DialogInterface.OnClickListener clickListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        strategy = new FritzOs7();
                                        prefs.edit().putBoolean("version7setup", false).apply();
                                        break;
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        strategy = new FritzOs6();
                                        break;
                                }
                                prefs.edit().putInt("version", strategy.getVersion()).apply();
                                dialog.dismiss();
                                readState();
                            }
                        };
                new AlertDialog.Builder(getActivity()).setMessage(R.string.ask_version)
                        .setPositiveButton("FRITZ!OS 07.xx", clickListener)
                        .setNegativeButton("FRITZ!OS 06.xx", clickListener).create().show();
            } else {
                readState();
            }
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
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 23 && getActivity().getPackageManager()
                .checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        getActivity().getPackageName()) != PackageManager.PERMISSION_GRANTED) {
            getActivity()
                    .requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            1);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        FRITZBOX_USER = Util.decrypt(prefs.getString("fb_user", null), getActivity());
        FRITZBOX_PW = Util.decrypt(prefs.getString("fb_pw", null), getActivity());
        FRITZBOX_ADDRESS = prefs.getString("address", "fritz.box");

        if (FRITZBOX_ADDRESS.contains("http://")) {
            FRITZBOX_ADDRESS = FRITZBOX_ADDRESS.replace("http://", "");
        }

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
                    changeWiFi(!currentConfig.enabled);
                }
            }
        });

        v.findViewById(R.id.error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });

        if (PRO_VERSION) {
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
        if (BuildConfig.DEBUG) Logger.log("purchased");
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
                                Uri.parse("https://j4velin.de/faq/index.php?app=gz"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }).create();
        loginCredentials.show();
    }

    private void blocked(final int time) {
        if (BuildConfig.DEBUG) Logger.log("blocked " + time);
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
        if (BuildConfig.DEBUG) Logger.log("updateState " + currentConfig);
        boolean currently_enabled = currentConfig != null && currentConfig.enabled;
        image.setVisibility(currently_enabled ? View.VISIBLE : View.GONE);
        scanToConnect.setVisibility(currently_enabled ? View.VISIBLE : View.GONE);
        ssid.setEnabled(!currently_enabled);
        key.setEnabled(!currently_enabled);
        if (currentConfig != null) {
            if (PRO_VERSION) {
                ssid.setText(currentConfig.ssid);
                key.setText(currentConfig.key);
            }
            if (currently_enabled) {
                setQrCode(currentConfig.toString());
                Notification.Builder b = new Notification.Builder(getActivity())
                        .setContentTitle(getActivity().getString(R.string.guest_wifi_active))
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        API26Wrapper.setChannelId(b);
                    }
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
        if (BuildConfig.DEBUG) Logger.log("change wifi");
        pg = new ProgressDialog(getActivity());
        pg.setMessage(getString(R.string.please_wait));
        pg.show();
        final Handler h = new Handler();
        if (!PRO_VERSION) {
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

                    WiFiData newConfig = new WiFiData(currentConfig).setEnabled(enable);

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
                        newConfig.setSsid(wifi_ssid);
                        newConfig.setKey(key);
                        newConfig.setOnly_web(prefs.getBoolean("wifi_limited_access", true));
                        newConfig.setAllow_communication(
                                prefs.getBoolean("wifi_communicate", false));
                    }

                    if (BuildConfig.DEBUG) Logger.log("setting new state to " + newConfig);

                    boolean success = strategy.setConfig(FRITZBOX_ADDRESS, SID, newConfig);

                    if (!success) {
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
                "http://" + FRITZBOX_ADDRESS + "/login_sid.lua?username=" + FRITZBOX_USER :
                "http://" + FRITZBOX_ADDRESS + "/login_sid.lua";
        try {
            return getLogin(new URL(url));
        } catch (final Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
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
            return null;
        }
    }

    private static Parser.LoginEntry getLogin(final URL url) throws IOException,
            XmlPullParserException, NoSuchAlgorithmException {
        if (BuildConfig.DEBUG) Logger.log("getting login from " + url);
        Parser p = new Parser();
        InputStream in = url.openStream();
        List<Parser.Entry> entries = p.parse(in);
        in.close();
        if (BuildConfig.DEBUG) Logger.log("read entries: " + entries.size());
        Parser.LoginEntry login = null;
        for (int i = 0; i < entries.size() && login == null; i++) {
            if (entries.get(i) instanceof Parser.LoginEntry) {
                login = (Parser.LoginEntry) entries.get(i);
            }
        }
        if (BuildConfig.DEBUG) Logger.log("login: " + login);
        if (login.sid.equals("0000000000000000")) {
            if (url.toString().contains("response") || FRITZBOX_PW == null ||
                    FRITZBOX_PW.length() < 1) return login;
            String user = FRITZBOX_USER != null && FRITZBOX_USER.length() > 0 ?
                    "?username=" + FRITZBOX_USER + "&" : "?";
            return getLogin(
                    new URL("http://" + FRITZBOX_ADDRESS + "/login_sid.lua" + user + "response=" +
                            login.challenge + "-" + Util.md5(login.challenge + "-" + FRITZBOX_PW)));
        } else {
            return login;
        }
    }

    private void readState() {
        if (BuildConfig.DEBUG) Logger.log("readstate");
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
                    if (BuildConfig.DEBUG) Logger.log("reading current state");
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
                            if (BuildConfig.DEBUG) Logger.log("readstate -> askforlogin");
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
                    currentConfig = strategy.readConfig(FRITZBOX_ADDRESS, SID);
                    final SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(getActivity());
                    if (currentConfig == null) {
                        prefs.edit().remove("version").apply();
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getActivity(), "Unable to read guest access config",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (strategy.getVersion() == 7 &&
                            !prefs.getBoolean("version7setup", false)) {
                        if (BuildConfig.DEBUG) Logger.log("setup first usage with fritzos 7");
                        WiFiData newConfig = new WiFiData(currentConfig);
                        newConfig.setEnabled(false);
                        if (strategy.setConfig(FRITZBOX_ADDRESS, SID, newConfig)) {
                            prefs.edit().putBoolean("version7setup", true).apply();
                        }
                    }
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Logger.log(e);
                } finally {
                    if (pg != null && pg.isShowing()) {
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                if (SID != null) {
                                    updateState();
                                } else {
                                    askForLogin();
                                }
                                if (pg != null && pg.isShowing()) pg.dismiss();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private static String getRandomKey(long seed) {
        Random r = new Random(seed);
        char[] re = new char[10];
        int max = ALL_CHARS.length();
        for (int i = 0; i < 10; i++) {
            re[i] = ALL_CHARS.charAt(r.nextInt(max));
        }
        return String.valueOf(re);
    }
}
