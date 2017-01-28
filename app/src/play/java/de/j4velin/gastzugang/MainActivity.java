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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private final static String SKU = "de.j4velin.gastzugang.billing.pro";

    private IInAppBillingService mService;

    private MainFragment fragment;

    private final ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(final ComponentName name) {
            if (BuildConfig.DEBUG) Logger.log("vending disconnected");
            mService = null;
        }

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            if (BuildConfig.DEBUG) Logger.log("vending connected");
            mService = IInAppBillingService.Stub.asInterface(service);
            try {
                Bundle ownedItems = mService.getPurchases(3, getPackageName(), "inapp", null);
                if (BuildConfig.DEBUG) Logger.log("ownedItems: " + ownedItems);
                if (ownedItems.getInt("RESPONSE_CODE") == 0) {
                    MainFragment.PRO_VERSION =
                            ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST") != null &&
                                    ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST")
                                            .contains(SKU);
                    PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit()
                            .putBoolean("pro", MainFragment.PRO_VERSION).apply();
                    if (MainFragment.PRO_VERSION) {
                        unbindService(this);
                        mService = null;
                    }
                }
            } catch (RemoteException e) {
                Toast.makeText(MainActivity.this, e.getClass().getName() + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    };

    void purchase(final MainFragment fragment) {
        if (mService == null) {
            Toast.makeText(this, R.string.connect_license, Toast.LENGTH_LONG).show();
        } else {
            this.fragment = fragment;
            try {
                Bundle buyIntentBundle =
                        mService.getBuyIntent(3, getPackageName(), SKU, "inapp", getPackageName());
                if (buyIntentBundle.getInt("RESPONSE_CODE") == 0) {
                    PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                    startIntentSenderForResult(pendingIntent.getIntentSender(), 42, null, 0, 0, 0);
                }
            } catch (Exception e) {
                Toast.makeText(this, e.getClass().getName() + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 42 && resultCode == RESULT_OK) {
            if (data.getIntExtra("RESPONSE_CODE", 0) == 0) {
                try {
                    JSONObject jo = new JSONObject(data.getStringExtra("INAPP_PURCHASE_DATA"));
                    if (BuildConfig.DEBUG) Logger.log("onActivityResult: " + jo);
                    MainFragment.PRO_VERSION = jo.getString("productId").equals(SKU) &&
                            jo.getString("developerPayload").equals(getPackageName());
                    PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean("pro", MainFragment.PRO_VERSION).apply();
                    if (fragment != null) fragment.purchased();
                } catch (Exception e) {
                    Toast.makeText(this, e.getClass().getName() + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fragment newFragment = new MainFragment();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(android.R.id.content, newFragment);
        transaction.commit();

        MainFragment.PRO_VERSION |=
                PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pro", false);
        if (!MainFragment.PRO_VERSION) {
            if (BuildConfig.DEBUG) Logger.log("binding to service");
            bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND")
                    .setPackage("com.android.vending"), mServiceConn, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            unbindService(mServiceConn);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, final MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        return super.onMenuItemSelected(featureId, item);
    }
}
