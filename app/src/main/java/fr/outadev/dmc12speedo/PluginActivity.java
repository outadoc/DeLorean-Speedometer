package fr.outadev.dmc12speedo;

import java.util.Timer;
import java.util.TimerTask;

import org.prowl.torque.remote.ITorqueService;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
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

public class PluginActivity extends Activity {

	private final boolean DEBUG = false;
	private final long PID_SPEED = 0x0D;

	private final int[] backgrounds = new int[] { 0, R.drawable.bg, R.drawable.bg2, R.drawable.bg3, R.drawable.bg4 };
	private int currentBg;

	private TextView txt_speed_diz;
	private TextView txt_speed_unit;
	private Handler handler;

	private Timer updateTimer;
	private ITorqueService torqueService;

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_plugin);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		LayoutInflater inflater = LayoutInflater.from(this);
		final View view = inflater.inflate(R.layout.activity_plugin, null);

		View rootView = getWindow().getDecorView();
		rootView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

		txt_speed_diz = (TextView) view.findViewById(R.id.txt_speed_diz);
		txt_speed_unit = (TextView) view.findViewById(R.id.txt_speed_unit);

		Typeface font = Typeface
				.createFromAsset(getAssets(), "digital-7_mono_italic.ttf");

		txt_speed_diz.setTypeface(font);
		txt_speed_unit.setTypeface(font);
		((TextView) view.findViewById(R.id.txt_dot)).setTypeface(font);

		currentBg = 0;

		view.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				view.setBackgroundResource(backgrounds[++currentBg % backgrounds.length]);
			}

		});

		handler = new Handler();
		setContentView(view);
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

	/**
	 * Do an update
	 */
	@SuppressWarnings("deprecation")
	public void update() {
		try {
			final long speed = (long) torqueService
					.getValueForPid(PID_SPEED, true);

			// Update the widget.
			handler.post(new Runnable() {
				public void run() {
					if(speed > 99) {
						txt_speed_diz.setText("-");
						txt_speed_unit.setText("-");
					} else if(speed > 9) {
						txt_speed_diz.setText(Long.valueOf(speed / 10)
								.toString());
						txt_speed_unit.setText(Long.valueOf(speed % 10)
								.toString());
					} else {
						txt_speed_diz.setText("");
						txt_speed_unit.setText(Long.valueOf(speed).toString());
					}
				}
			});
		} catch(RemoteException e) {
			Log.e(getClass().getCanonicalName(), e.getMessage(), e);
		}
	}

	/**
	 * Bits of service code. You usually won't need to change this.
	 */
	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName arg0, IBinder service) {
			torqueService = ITorqueService.Stub.asInterface(service);

			try {
				torqueService.setDebugTestMode(DEBUG);
			} catch(RemoteException e) {
				e.printStackTrace();
			}
		};

		public void onServiceDisconnected(ComponentName name) {
			torqueService = null;
		};
	};
}