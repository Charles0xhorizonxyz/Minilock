package com.miniscreen.miniwatch;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;

/**
 * What a flick of the dial can do on the stand-in lock screen. The page names the gesture
 * ("left1": one flick right to left; "right2": two flicks left to right); the user picks the
 * action for each in the app; the lock screen performs it here.
 */
final class Gestures {

    private Gestures() { }

    static final String LEFT1 = "g_left1", RIGHT2 = "g_right2";
    static final String[] KEYS = {"none", "unlock", "camera", "torch", "app", "alarms"};
    static final String[] NAMES = {"Nothing", "Unlock", "Open the camera", "Torch on or off",
                                   "Open Miniwatch", "Open the alarms"};

    static String defaultFor(String prefKey) { return LEFT1.equals(prefKey) ? "unlock" : "camera"; }

    static int indexOf(String key) { return indexOf(KEYS, key); }

    static int indexOf(String[] keys, String key) {
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(key)) return i;
        return 0;
    }

    /** Runs the user's choice for a gesture the page reported. Only the lock screen calls this. */
    static void perform(Activity from, String gesture, Runnable unlock, Torch torch) {
        String prefKey = "right2".equals(gesture) ? RIGHT2 : LEFT1;
        String action = Prefs.gesture(from, prefKey, defaultFor(prefKey));
        switch (action) {
            case "unlock": unlock.run(); break;
            case "camera": open(from, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); break;
            case "torch": torch.toggle(); break;
            case "app": open(from, new Intent(from, MainActivity.class)); break;
            case "alarms": open(from, new Intent(AlarmClock.ACTION_SHOW_ALARMS)); break;
            default: break;
        }
    }

    private static void open(Activity from, Intent intent) {
        try {
            from.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) { }
    }

    /** The flash as a torch, tracked through the system so its state is never guessed. */
    static final class Torch extends CameraManager.TorchCallback {
        private final CameraManager cameras;
        private String id;
        private boolean on;

        Torch(Context context) {
            cameras = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            try {
                for (String candidate : cameras.getCameraIdList()) {
                    CameraCharacteristics c = cameras.getCameraCharacteristics(candidate);
                    if (Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) {
                        id = candidate;
                        break;
                    }
                }
                cameras.registerTorchCallback(this, null);
            } catch (Exception ignored) { }
        }

        @Override public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (cameraId.equals(id)) on = enabled;
        }

        void toggle() {
            if (id == null) return;
            try { cameras.setTorchMode(id, !on); } catch (Exception ignored) { }
        }

        void release() {
            try { cameras.unregisterTorchCallback(this); } catch (Exception ignored) { }
        }
    }
}
