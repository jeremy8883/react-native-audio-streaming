package com.audioStreaming;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.spoledge.aacdecoder.MultiPlayer;
import com.spoledge.aacdecoder.PlayerCallback;

import static android.telephony.PhoneStateListener.LISTEN_NONE;

public class Signal extends Service implements OnErrorListener,
        OnCompletionListener,
        OnPreparedListener,
        OnInfoListener,
        PlayerCallback {

    private PlayerNotification playerNotification;
    private MultiPlayer aacPlayer;

    private static final int AAC_BUFFER_CAPACITY_MS = 2500;
    private static final int AAC_DECODER_CAPACITY_MS = 700;

    public static final String BROADCAST_PLAYBACK_STOP = "stop",
            BROADCAST_PLAYBACK_PLAY = "pause",
            BROADCAST_EXIT = "exit";

    private final IBinder binder = new RadioBinder();
    private final SignalReceiver receiver = new SignalReceiver(this);
    private String streamingURL;
    public boolean isPlaying = false;
    public String streamTitle = null;
    private boolean isPreparingStarted = false;
    private EventsReceiver eventsReceiver;
    private ReactNativeAudioStreamingModule module;

    private TelephonyManager phoneManager;
    private PhoneListener phoneStateListener;

    public void setData(Context context, ReactNativeAudioStreamingModule module) {
        this.playerNotification = new PlayerNotification(
                module.getClassActivity(),
                context,
                this
        );
        this.module = module;

        this.eventsReceiver = new EventsReceiver(this.module);


        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CREATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.DESTROYED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STARTED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CONNECTING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.START_PREPARING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PREPARED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PLAYING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STOPPED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.COMPLETED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ERROR));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_START));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_END));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.METADATA_UPDATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ALBUM_UPDATED));


        System.out.println("TEST: setData called " + (this.phoneStateListener == null));

        // I don't think `setData` ever gets called twice, but just to be safe.
        destroyPhoneListeners();

        this.phoneStateListener = new PhoneListener(this);
        this.phoneManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (this.phoneManager != null) {
            this.phoneManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    @Override
    public void onCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_PLAYBACK_STOP);
        intentFilter.addAction(BROADCAST_PLAYBACK_PLAY);
        intentFilter.addAction(BROADCAST_EXIT);
        registerReceiver(this.receiver, intentFilter);


        try {
            this.aacPlayer = new MultiPlayer(this, AAC_BUFFER_CAPACITY_MS, AAC_DECODER_CAPACITY_MS);
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            java.net.URL.setURLStreamHandlerFactory(new java.net.URLStreamHandlerFactory() {
                public java.net.URLStreamHandler createURLStreamHandler(String protocol) {
                    if ("icy".equals(protocol)) {
                        return new com.spoledge.aacdecoder.IcyURLStreamHandler();
                    }
                    return null;
                }
            });
        } catch (Throwable t) {

        }

        sendBroadcast(new Intent(Mode.CREATED));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        destroyNotification();
        destroyPhoneListeners();
        stop();
    }

    private void destroyPhoneListeners() {
        if (this.phoneManager != null & this.phoneStateListener != null) {
            this.phoneManager.listen(this.phoneStateListener, LISTEN_NONE);

            this.phoneStateListener = null;
            this.phoneManager = null;
        }
    }

    public void setURLStreaming(String streamingURL) {
        this.streamingURL = streamingURL;
    }

    public void play() {
        if (isConnected()) {
            this.prepare();
        } else {
            sendBroadcast(new Intent(Mode.STOPPED));
        }

        this.isPlaying = true;
        updateNotificationAndShow();
    }


    public void stop() {
        this.isPreparingStarted = false;

        if (this.isPlaying) {
            this.isPlaying = false;
            this.aacPlayer.stop();
        }

        sendBroadcast(new Intent(Mode.STOPPED));
        updateNotificationAndShow();
    }

    public class RadioBinder extends Binder {
        public Signal getService() {
            return Signal.this;
        }
    }

    public void showNotification(int color, String text) {
        streamTitle = null; // Will get set to its proper value again once the player starts

        playerNotification.showNotification(color, text);
    }

    public void destroyNotification() {
        if (playerNotification != null) {
            playerNotification.destroy();
        }
    }

    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }

    public void prepare() {
        /* ------Station- buffering-------- */
        this.isPreparingStarted = true;
        sendBroadcast(new Intent(Mode.START_PREPARING));

        try {
            this.aacPlayer.playAsync(this.streamingURL);
        } catch (Exception e) {
            e.printStackTrace();
            stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.isPlaying) {
            sendBroadcast(new Intent(Mode.PLAYING));
        } else if (this.isPreparingStarted) {
            sendBroadcast(new Intent(Mode.START_PREPARING));
        } else {
            sendBroadcast(new Intent(Mode.STARTED));
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onPrepared(MediaPlayer _mediaPlayer) {
        this.isPreparingStarted = false;
        sendBroadcast(new Intent(Mode.PREPARED));
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        this.isPlaying = false;
        this.aacPlayer.stop();

        updateNotificationAndShow();
        sendBroadcast(new Intent(Mode.COMPLETED));
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == 701) {
            this.isPlaying = false;
            sendBroadcast(new Intent(Mode.BUFFERING_START));
        } else if (what == 702) {
            this.isPlaying = true;
            sendBroadcast(new Intent(Mode.BUFFERING_END));
        }
        updateNotificationAndShow();
        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                //Log.v("ERROR", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK "	+ extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                //Log.v("ERROR", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                //Log.v("ERROR", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        sendBroadcast(new Intent(Mode.ERROR));
        return false;
    }

    @Override
    public void playerStarted() {
        //  TODO
    }

    @Override
    public void playerPCMFeedBuffer(boolean isPlaying, int bufSizeMs, int bufCapacityMs) {
        if (isPlaying) {
            this.isPreparingStarted = false;
            if (bufSizeMs < 500) {
                this.isPlaying = false;
                sendBroadcast(new Intent(Mode.BUFFERING_START));
                //buffering
            } else {
                this.isPlaying = true;
                sendBroadcast(new Intent(Mode.PLAYING));
                //playing
            }
        } else {
            //buffering
            this.isPlaying = false;
            sendBroadcast(new Intent(Mode.BUFFERING_START));
        }
        updateNotificationAndShow();
    }

    @Override
    public void playerException(final Throwable t) {
        this.isPlaying = false;
        this.isPreparingStarted = false;
        sendBroadcast(new Intent(Mode.ERROR));
        updateNotificationAndShow();
        //  TODO
    }

    @Override
    public void playerMetadata(final String key, final String value) {
        Intent metaIntent = new Intent(Mode.METADATA_UPDATED);
        metaIntent.putExtra("key", key);
        metaIntent.putExtra("value", value);
        sendBroadcast(metaIntent);

        if (key != null && key.equals("StreamTitle") && value != null) {
            this.streamTitle = value;
            updateNotificationAndShow();
        }
    }

    @Override
    public void playerAudioTrackCreated(AudioTrack atrack) {
        //  TODO
    }

    @Override
    public void playerStopped(int perf) {
        this.isPlaying = false;
        this.isPreparingStarted = false;

        sendBroadcast(new Intent(Mode.STOPPED));
        updateNotificationAndShow();
        //  TODO
    }

    private void updateNotificationAndShow() {
        if (playerNotification == null) return; // ie. notification hasn't been shown yet

        playerNotification.updateNotification(this.streamTitle, this.isPlaying);
    }
}
