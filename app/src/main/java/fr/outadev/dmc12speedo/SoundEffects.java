package fr.outadev.dmc12speedo;

import android.content.Context;
import android.media.MediaPlayer;

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
	private int currentSound;
	private MediaPlayer mediaPlayer;

	public SoundEffects(Context context) {
		this.context = context;
		currentSound = -1;
	}

	public void playSound(int sound, boolean loop) {
		if(sound != getCurrentPlayingSound()) {
			stopSound();

			mediaPlayer = MediaPlayer.create(context, sound);
			mediaPlayer.setLooping(loop);
			mediaPlayer.start();
		}

		currentSound = sound;
	}

	public void stopSound() {
		if(mediaPlayer != null && mediaPlayer.isPlaying()) {
			mediaPlayer.stop();
		}
	}

	public int getCurrentPlayingSound() {
		return currentSound;
	}

}
