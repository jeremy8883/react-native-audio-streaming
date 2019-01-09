package com.audioStreaming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.util.Log;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import javax.annotation.Nullable;

public class ReactNativeAudioStreamingModule extends ReactContextBaseJavaModule
    implements ServiceConnection {

  private ReactApplicationContext context;

  private Class<?> clsActivity;
  private static Signal signal;
  private Intent bindIntent;
  private String streamingURL;
  private boolean shouldShowNotification;
  private int notificationColor;
  private String notificationText;

  public ReactNativeAudioStreamingModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
  }

  public ReactApplicationContext getReactApplicationContextModule() {
    return this.context;
  }

  public Class<?> getClassActivity() {
    if (this.clsActivity == null) {
      this.clsActivity = getCurrentActivity().getClass();
    }
    return this.clsActivity;
  }

  public void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  @Override public String getName() {
    return "ReactNativeAudioStreaming";
  }

  @Override public void initialize() {
    super.initialize();

    try {
      bindIntent = new Intent(this.context, Signal.class);
      this.context.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    } catch (Exception e) {
      Log.e("ERROR", e.getMessage());
    }
  }

  @Override public void onServiceConnected(ComponentName className, IBinder service) {
    signal = ((Signal.RadioBinder) service).getService();
    signal.setData(this.context, this);
    WritableMap params = Arguments.createMap();
    sendEvent(this.getReactApplicationContextModule(), "streamingOpen", params);
  }

  @Override public void onServiceDisconnected(ComponentName className) {
    signal = null;
  }

  @ReactMethod public void play(String streamingURL, boolean showNotification, String notificationText, String notificationColor) {
    this.streamingURL = streamingURL;
    this.shouldShowNotification = showNotification;

    this.notificationColor = notificationColor == null ? Color.BLACK : Color.parseColor(notificationColor);

    this.notificationText = notificationText;

    signal.setURLStreaming(streamingURL); // URL of MP3 or AAC stream
    playInternal();
  }

  private void playInternal() {
    signal.play();
    if (shouldShowNotification) {
      signal.showNotification(this.notificationColor, this.notificationText);
    }
  }

  @ReactMethod public void stop() {
    signal.stop(true, true);
  }

  @ReactMethod public void pause() {
    signal.stop(false, true);
  }

  @ReactMethod public void resume() {
    // Not implemented on aac
    playInternal();
  }

  @ReactMethod public void destroyNotification() {
    signal.destroyNotification();
  }

  @ReactMethod public void getStatus(Callback callback) {
    WritableMap state = Arguments.createMap();
    state.putString("status", signal != null && signal.isPlaying ? Mode.PLAYING : Mode.STOPPED);
    callback.invoke(null, state);
  }
}
