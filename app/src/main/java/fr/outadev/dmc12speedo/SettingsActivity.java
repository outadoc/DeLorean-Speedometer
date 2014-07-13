package fr.outadev.dmc12speedo;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by outadoc on 13/07/14.
 */
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

	public static final String KEY_PREF_USE_MPH = "pref_use_mph";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		/*if(key.equals(KEY_PREF_USE_MPH)) {
			Preference mphPref = findPreference(key);
			if(sharedPreferences.getBoolean(key, false)) {
				mphPref.setSummary("use mph lol");
			} else {
				mphPref.setSummary("use km/h lol");
			}
		}*/
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

}