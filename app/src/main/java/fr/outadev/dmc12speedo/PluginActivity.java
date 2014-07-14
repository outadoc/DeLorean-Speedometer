package fr.outadev.dmc12speedo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
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

	private static final long PID_SPEED = 0x0D;

	private static final int OBD_REFRESH_INTERVAL = 500;

	private static final int BG_INDEX_NONE = -1;
	private static final int BG_INDEX_CAMERA = -2;

	private static final int[] backgrounds = new int[]{BG_INDEX_NONE, 0, R.drawable.bg, R.drawable.bg2, R.drawable.bg3,
			R.drawable.bg4,
			BG_INDEX_CAMERA};

	private boolean lastConnected = false;

	private int currentBg;

	private boolean useMph = false;
	private boolean debug = false;
	private boolean incSpeed = true;

	private double lastSpeed;
	private double currentSpeed;

	private TextView txt_speed_diz;
	private TextView txt_speed_unit;
	private FrameLayout cameraPreview;
	private View backgroundView;

	private Handler handler;
	private Timer updateSpeedTimer;

	private ITorqueService torqueService;
	private SharedPreferences prefs;

	private SoundEffects sfx;
	private long lastTimeTravelTime;

	private ServiceConnection connection;

	private Camera mCamera;

	public static Camera getCameraInstance() {
		Camera c = null;

		try {
			c = Camera.open();
		} catch(Exception e) {
			//silently ignore and return null if couldn't open camera
		}

		return c;
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		connection = new ServiceConnection() {

			public void onServiceConnected(ComponentName arg0, IBinder service) {
				torqueService = ITorqueService.Stub.asInterface(service);
			}

			public void onServiceDisconnected(ComponentName name) {
				torqueService = null;
			}

		};

		setContentView(R.layout.activity_plugin);

		View rootView = getWindow().getDecorView();

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		} else {
			rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
		}

		txt_speed_diz = (TextView) findViewById(R.id.txt_speed_diz);
		txt_speed_unit = (TextView) findViewById(R.id.txt_speed_unit);

		View speedo_view = findViewById(R.id.speedo_view);

		Typeface font = Typeface
				.createFromAsset(getAssets(), "digital-7_mono_italic.ttf");

		txt_speed_diz.setTypeface(font);
		txt_speed_unit.setTypeface(font);
		((TextView) findViewById(R.id.txt_dot)).setTypeface(font);

		backgroundView = findViewById(R.id.background_view);

		currentBg = prefs.getInt("currentBg", 0);

		speedo_view.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(PluginActivity.this, SettingsActivity.class);
				startActivity(intent);
			}

		});

		backgroundView.setOnClickListener(new OnClickListener() {

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
		incSpeed = prefs.getBoolean("pref_speed_increment", true);
		debug = prefs.getBoolean("pref_debug", false);
		sfx.setEnabled(prefs.getBoolean("pref_enable_sounds", true));

		txt_speed_diz.setText("-");
		txt_speed_unit.setText("-");

		// Bind to the torque service
		Intent intent = new Intent();
		intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService");
		boolean successfulBind = bindService(intent, connection, 0);

		if(successfulBind) {
			updateSpeedTimer = new Timer();
			updateSpeedTimer.schedule(new TimerTask() {

				public void run() {
					updateSpeed();
				}

			}, 500, OBD_REFRESH_INTERVAL);
		}

		if(getCurrentBackgroundResource() == BG_INDEX_CAMERA) {
			initCamera();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		updateSpeedTimer.cancel();
		unbindService(connection);

		releaseCamera();
	}

	private void updateBackground() {
		int bg = getCurrentBackgroundResource();

		if(mCamera != null) {
			releaseCamera();
			cameraPreview.setVisibility(View.GONE);
		}

		if(bg == BG_INDEX_NONE) {
			backgroundView.setBackgroundColor(getResources().getColor(android.R.color.black));
		} else if(bg == BG_INDEX_CAMERA) {
			initCamera();

			if(mCamera == null) {
				currentBg++;
				updateBackground();
				return;
			}

			cameraPreview.setVisibility(View.VISIBLE);
		} else {
			backgroundView.setBackgroundResource(bg);
		}
	}

	@SuppressWarnings("deprecation")
	public void updateSpeed() {
		try {
			if(torqueService.isConnectedToECU() || debug) {
				double speed;

				lastSpeed = currentSpeed;

				if(debug) {
					speed = getDebugCurrentSpeed();
					Log.d("DMC12", speed + " km/h");
				} else {
					speed = (double) torqueService.getValueForPid(PID_SPEED, true);
				}

				if(useMph) {
					speed *= 0.621371;
				}

				if(incSpeed) {
					incrementSpeedUpTo(speed);
				} else {
					currentSpeed = speed;
					updateSpeedometer();
				}

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

	private void incrementSpeedUpTo(final double targetSpeed) {
		(new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				while((long) currentSpeed != (long) targetSpeed) {

					try {
						if(currentSpeed < targetSpeed) {
							currentSpeed += 1.0;
						} else {
							currentSpeed -= 1.0;
						}

						PluginActivity.this.runOnUiThread(new Runnable() {

							@Override
							public void run() {
								updateSpeedometer();
							}

						});

						long remainingUnits = Math.abs((long) targetSpeed - (long) lastSpeed);

						if(remainingUnits == 0 || remainingUnits > 10) {
							throw new InterruptedException();
						}

						Thread.sleep(OBD_REFRESH_INTERVAL / remainingUnits);
					} catch(InterruptedException e) {
						currentSpeed = targetSpeed;
					}
				}

				return null;
			}

		}).execute();
	}

	private void updateSpeedometer() {
		// Update the widget.
		handler.post(new Runnable() {

			public void run() {
				if(currentSpeed >= 100.0) {
					txt_speed_diz.setText("-");
					txt_speed_unit.setText("-");
				} else if(currentSpeed >= 10.0) {
					txt_speed_diz.setText(Long.valueOf((long) currentSpeed / 10)
							.toString());
					txt_speed_unit.setText(Long.valueOf((long) currentSpeed % 10)
							.toString());
				} else {
					txt_speed_diz.setText("");
					txt_speed_unit.setText(Long.valueOf((long) currentSpeed).toString());
				}

				if((new Date()).getTime() - lastTimeTravelTime >= 10 * 1000) {
					if(currentSpeed >= 88.0 && currentSpeed < 92.0) {
						sfx.playSound(SoundEffects.TIME_TRAVEL, false);
						lastTimeTravelTime = (new Date()).getTime();
					} else if(currentSpeed >= 80.0 && currentSpeed < 88.0) {
						sfx.playSound(SoundEffects.PREPARE_TIME_TRAVEL, true);
					} else if(sfx.getCurrentPlayingSound() == SoundEffects.PREPARE_TIME_TRAVEL) {
						sfx.stopSound();
					}
				}
			}

		});
	}

	private void initCamera() {
		// Create an instance of Camera
		mCamera = getCameraInstance();

		if(mCamera != null) {
			CameraPreview preview = new CameraPreview(this, mCamera);
			cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
			cameraPreview.addView(preview);

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

	private int getCurrentBackgroundResource() {
		return backgrounds[currentBg % backgrounds.length];
	}

	private long getDebugCurrentSpeed() {
		return 5 * (((new Date()).getTime() / 1000) % 30);
	}
}