package fr.outadev.dmc12speedo;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * Manages the time travel sound effects.
 * Created by outadoc on 13/07/14.
 */
public class SoundEffects {

	public static final int STARTUP = R.raw.engine_on;
	public static final int SHUTDOWN = R.raw.engine_off;
	public static final int PREPARE_TIME_TRAVEL = R.raw.prepare_time_travel;
	public static final int TIME_TRAVEL = R.raw.time_travel;

	private Context context;
	private boolean enabled;

	private int currentSound;
	private MediaPlayer mediaPlayer;

	public SoundEffects(Context context) {
		this.context = context;
		this.enabled = true;

		currentSound = -1;
	}

	public void playSound(int sound, boolean loop) {
		if(enabled) {
			if(sound != getCurrentPlayingSound()) {
				stopSound();

				Log.d("DMC12", "start playing sound " + context.getResources().getResourceEntryName(sound));

				mediaPlayer = MediaPlayer.create(context, sound);
				mediaPlayer.setLooping(loop);
				mediaPlayer.start();
			}

			currentSound = sound;
		}
	}

	public void stopSound() {
		if(mediaPlayer != null && mediaPlayer.isPlaying()) {
			mediaPlayer.stop();
		}
	}

	public int getCurrentPlayingSound() {
		return currentSound;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
