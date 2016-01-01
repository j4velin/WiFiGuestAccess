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

    public class SettingsFragment extends PreferenceFragment {
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
            findPreference("address")
                    .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(final Preference preference, final Object o) {
                            MainFragment.FRITZBOX_ADDRESS = (String) o;
                            return true;
                        }
                    });
        }
    }
}
