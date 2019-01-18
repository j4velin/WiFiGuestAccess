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
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fragment newFragment = new SettingsFragment();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(android.R.id.content, newFragment);
        transaction.commit();
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);
            findPreference("delete_login")
                    .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(final Preference preference) {
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                                    .remove("fb_pw").remove("fb_user").apply();
                            Toast.makeText(getActivity(), android.R.string.ok, Toast.LENGTH_SHORT)
                                    .show();
                            MainFragment.FRITZBOX_PW = null;
                            MainFragment.FRITZBOX_USER = null;
                            MainFragment.SID = null;
                            return true;
                        }
                    });
            findPreference("delete_version")
                    .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(final Preference preference) {
                            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                                    .remove("version").remove("version7setup").apply();
                            Toast.makeText(getActivity(), android.R.string.ok, Toast.LENGTH_SHORT)
                                    .show();
                            return true;
                        }
                    });
            findPreference("address")
                    .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(final Preference preference,
                                                          final Object o) {
                            String address = (String) o;
                            if (address.contains("http://")) {
                                address = address.replace("http://", "");
                            }
                            MainFragment.FRITZBOX_ADDRESS = address;
                            return true;
                        }
                    });
        }
    }
}
