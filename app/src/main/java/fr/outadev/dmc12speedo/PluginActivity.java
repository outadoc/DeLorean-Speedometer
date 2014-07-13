package fr.outadev.dmc12speedo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.TextView;

import org.prowl.torque.remote.ITorqueService;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class PluginActivity extends Activity {

	private final long PID_SPEED = 0x0D;

	private final int[] backgrounds = new int[]{-1, 0, R.drawable.bg, R.drawable.bg2, R.drawable.bg3, R.drawable.bg4};
	private boolean lastConnected = false;
	private int currentBg;

	private TextView txt_speed_diz;
	private TextView txt_speed_unit;
	private View container;

	private Handler handler;
	private Timer updateTimer;

	private ITorqueService torqueService;

	private SoundEffects sfx;
	private long lastTimeTravelTime;

	private ServiceConnection connection;

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_plugin);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		LayoutInflater inflater = LayoutInflater.from(this);
		container = inflater.inflate(R.layout.activity_plugin, null);

		connection = new ServiceConnection() {

			public void onServiceConnected(ComponentName arg0, IBinder service) {
				torqueService = ITorqueService.Stub.asInterface(service);

				try {
					torqueService.setDebugTestMode(isDebugEnabled());
				} catch(RemoteException e) {
					e.printStackTrace();
				}
			}

			public void onServiceDisconnected(ComponentName name) {
				torqueService = null;
			}

		};

		final SharedPreferences prefs = getSharedPreferences("fr.outadev.dmc12speedometer", MODE_PRIVATE);

		View rootView = getWindow().getDecorView();
		rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

		txt_speed_diz = (TextView) container.findViewById(R.id.txt_speed_diz);
		txt_speed_unit = (TextView) container.findViewById(R.id.txt_speed_unit);

		Typeface font = Typeface
				.createFromAsset(getAssets(), "digital-7_mono_italic.ttf");

		txt_speed_diz.setTypeface(font);
		txt_speed_unit.setTypeface(font);
		((TextView) container.findViewById(R.id.txt_dot)).setTypeface(font);

		currentBg = prefs.getInt("currentBg", 0);
		updateBackground();

		container.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				currentBg++;
				updateBackground();

				SharedPreferences.Editor edit = prefs.edit();
				edit.putInt("currentBg", currentBg);
				edit.apply();
			}

		});

		sfx = new SoundEffects(this);
		handler = new Handler();
		setContentView(container);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Bind to the torque service
		Intent intent = new Intent();
		intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService");
		boolean successfulBind = bindService(intent, connection, 0);

		if(successfulBind) {
			updateTimer = new Timer();
			updateTimer.schedule(new TimerTask() {

				public void run() {
					update();
				}

			}, 1000, 500);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		updateTimer.cancel();
		unbindService(connection);
	}

	private boolean isDebugEnabled() {
		return getResources().getBoolean(R.bool.debug_mode);
	}

	private void updateBackground() {
		int bg = backgrounds[currentBg % backgrounds.length];

		if(bg == -1) {
			container.setBackgroundColor(getResources().getColor(android.R.color.black));
		} else {
			container.setBackgroundResource(bg);
		}
	}

	/**
	 * Do an update
	 */
	@SuppressWarnings("deprecation")
	public void update() {
		try {
			if(torqueService.isConnectedToECU() || isDebugEnabled()) {
				final long speed = (long) torqueService
						.getValueForPid(PID_SPEED, true);

				// Update the widget.
				handler.post(new Runnable() {

					public void run() {
						if(speed >= 100.0) {
							txt_speed_diz.setText("-");
							txt_speed_unit.setText("-");
						} else if(speed >= 10.0) {
							txt_speed_diz.setText(Long.valueOf(speed / 10)
									.toString());
							txt_speed_unit.setText(Long.valueOf(speed % 10)
									.toString());
						} else {
							txt_speed_diz.setText("");
							txt_speed_unit.setText(Long.valueOf(speed).toString());
						}

						if((new Date()).getTime() - lastTimeTravelTime >= 10 * 1000) {
							if(speed >= 88.2 && speed <= 92.0) {
								sfx.playSound(SoundEffects.TIME_TRAVEL, false);
								lastTimeTravelTime = (new Date()).getTime();
							} else if(speed >= 80.0) {
								sfx.playSound(SoundEffects.PREPARE_TIME_TRAVEL, true);
							} else if(sfx.getCurrentPlayingSound() == SoundEffects.PREPARE_TIME_TRAVEL) {
								sfx.stopSound();
							}
						}
					}

				});

				if(!lastConnected) {
					// play startup sound
					sfx.playSound(SoundEffects.STARTUP, false);
				}

				lastConnected = true;
			} else {
				if(lastConnected) {
					//play shutdown sound
					sfx.playSound(SoundEffects.SHUTDOWN, false);
				}

				lastConnected = false;
			}
		} catch(RemoteException e) {
			Log.e(getClass().getCanonicalName(), e.getMessage(), e);
		}
	}
}