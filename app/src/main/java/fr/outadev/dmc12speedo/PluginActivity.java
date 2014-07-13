package fr.outadev.dmc12speedo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.prowl.torque.remote.ITorqueService;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class PluginActivity extends Activity {

	private final long PID_SPEED = 0x0D;

	private final int BG_INDEX_NONE = -1;
	private final int BG_INDEX_CAMERA = -2;

	private final int[] backgrounds = new int[]{BG_INDEX_NONE, 0, R.drawable.bg, R.drawable.bg2, R.drawable.bg3, R.drawable.bg4,
			BG_INDEX_CAMERA};
	private boolean lastConnected = false;

	private int currentBg;

	private boolean useMph = false;
	private boolean debug = false;

	private TextView txt_speed_diz;
	private TextView txt_speed_unit;
	private View container;
	private FrameLayout cameraPreview;

	private Handler handler;
	private Timer updateTimer;

	private ITorqueService torqueService;
	private SharedPreferences prefs;

	private SoundEffects sfx;
	private long lastTimeTravelTime;

	private ServiceConnection connection;

	private Camera mCamera;
	private CameraPreview mPreview;

	/**
	 * A safe way to get an instance of the Camera object.
	 */
	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch(Exception e) {
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		debug = prefs.getBoolean("pref_debug", false);

		connection = new ServiceConnection() {

			public void onServiceConnected(ComponentName arg0, IBinder service) {
				torqueService = ITorqueService.Stub.asInterface(service);

				try {
					torqueService.setDebugTestMode(debug);
				} catch(RemoteException e) {
					e.printStackTrace();
				}
			}

			public void onServiceDisconnected(ComponentName name) {
				torqueService = null;
			}

		};

		LayoutInflater inflater = LayoutInflater.from(this);
		container = inflater.inflate(R.layout.activity_plugin, null);
		setContentView(container);

		View rootView = getWindow().getDecorView();

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		} else {
			rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
		}

		txt_speed_diz = (TextView) container.findViewById(R.id.txt_speed_diz);
		txt_speed_unit = (TextView) container.findViewById(R.id.txt_speed_unit);

		View speedo_view = container.findViewById(R.id.speedo_view);

		Typeface font = Typeface
				.createFromAsset(getAssets(), "digital-7_mono_italic.ttf");

		txt_speed_diz.setTypeface(font);
		txt_speed_unit.setTypeface(font);
		((TextView) container.findViewById(R.id.txt_dot)).setTypeface(font);

		currentBg = prefs.getInt("currentBg", 0);

		speedo_view.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(PluginActivity.this, SettingsActivity.class);
				startActivity(intent);
			}

		});

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

		updateBackground();
	}

	@Override
	protected void onResume() {
		super.onResume();

		useMph = prefs.getBoolean("pref_use_mph", false);
		sfx.setEnabled(prefs.getBoolean("pref_enable_sounds", true));

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

	private void updateBackground() {
		int bg = backgrounds[currentBg % backgrounds.length];

		if(mCamera != null) {
			releaseCamera();
			cameraPreview.setVisibility(View.GONE);
		}

		if(bg == BG_INDEX_NONE) {
			container.setBackgroundColor(getResources().getColor(android.R.color.black));
		} else if(bg == BG_INDEX_CAMERA) {
			initCamera();

			if(mCamera == null) {
				currentBg++;
				updateBackground();
				return;
			}

			cameraPreview.setVisibility(View.VISIBLE);
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
			if(torqueService.isConnectedToECU() || debug) {
				long speed = (long) torqueService.getValueForPid(PID_SPEED, true);

				if(useMph) {
					speed *= 0.621371;
				}

				final long finalSpeed = speed;

				// Update the widget.
				handler.post(new Runnable() {

					public void run() {
						if(finalSpeed >= 100.0) {
							txt_speed_diz.setText("-");
							txt_speed_unit.setText("-");
						} else if(finalSpeed >= 10.0) {
							txt_speed_diz.setText(Long.valueOf(finalSpeed / 10)
									.toString());
							txt_speed_unit.setText(Long.valueOf(finalSpeed % 10)
									.toString());
						} else {
							txt_speed_diz.setText("");
							txt_speed_unit.setText(Long.valueOf(finalSpeed).toString());
						}

						if((new Date()).getTime() - lastTimeTravelTime >= 10 * 1000) {
							if(finalSpeed >= 88.2 && finalSpeed <= 92.0) {
								sfx.playSound(SoundEffects.TIME_TRAVEL, false);
								lastTimeTravelTime = (new Date()).getTime();
							} else if(finalSpeed >= 80.0) {
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

				handler.post(new Runnable() {

					public void run() {
						txt_speed_diz.setText("");
						txt_speed_unit.setText("");
					}

				});

				lastConnected = false;
			}
		} catch(RemoteException e) {
			Log.e(getClass().getCanonicalName(), e.getMessage(), e);
		}
	}

	private void initCamera() {
		// Create an instance of Camera
		mCamera = getCameraInstance();

		if(mCamera != null) {
			mPreview = new CameraPreview(this, mCamera);
			cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
			cameraPreview.addView(mPreview);

			mCamera.startPreview();
		}
	}

	private void releaseCamera() {
		if(mCamera != null) {
			cameraPreview.removeAllViews();

			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}
}