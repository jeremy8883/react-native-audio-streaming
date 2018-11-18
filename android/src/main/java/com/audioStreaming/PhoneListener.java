package com.audioStreaming;

import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneListener extends PhoneStateListener {

    private Signal signal;
    private boolean wasPlaying = false;

    public PhoneListener(Signal signal) {
        this.signal = signal;
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        Intent restart;

        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                if (wasPlaying) {
                    wasPlaying = false;
                    this.signal.play();
                }
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                // A call is dialing, active or on hold
                if (this.signal.isPlaying) {
                    this.wasPlaying = true;
                    this.signal.stop();
                }
                break;
            default:
                break;
        }
        super.onCallStateChanged(state, incomingNumber);
    }
}
