package fr.outadev.dmc12speedo;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Settings screen.
 * Created by outadoc on 13/07/14.
 */
@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs);
	}

}