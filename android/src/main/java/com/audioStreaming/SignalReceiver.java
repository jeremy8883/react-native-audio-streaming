package com.audioStreaming;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

class SignalReceiver extends BroadcastReceiver {
    private Signal signal;

    public SignalReceiver(Signal signal) {
        super();
        this.signal = signal;
    }

    private void playPauseToggle() {
        if (!this.signal.isPlaying) {
            this.signal.play();
        } else {
            this.signal.stop(false, true);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(Signal.BROADCAST_PLAYBACK_PLAY)) {
            playPauseToggle();
        } else if (action.equals(Signal.BROADCAST_EXIT)) {
            this.signal.stop(true, true);
        } else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
            int state = intent.getIntExtra("state", -1);
            if (state == 0) // Headset is unplugged
                this.signal.stop(false, true);
        }
    }
}
