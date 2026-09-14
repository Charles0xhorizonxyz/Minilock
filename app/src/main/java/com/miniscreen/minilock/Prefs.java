package com.miniscreen.minilock;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("minilock", Context.MODE_PRIVATE); }
    static int finish(Context c) { return get(c).getInt("finish", 0); }
    static boolean ambient(Context c) { return get(c).getBoolean("ambient", true); }
    static boolean sweep(Context c) { return get(c).getBoolean("sweep", true); }
    static boolean lock(Context c) { return get(c).getBoolean("lock", false); }
    static boolean card(Context c) { return get(c).getBoolean("card", true); }
    /**
     * "factory": the watch exactly as designed (24k gold, bright dial, dark studio), saved
     * choices kept but not applied. "custom": the caseback and background as the user left
     * them. Any change to those makes it custom again. The text under the watch is separate.
     */
    static boolean factory(Context c) { return "factory".equals(get(c).getString("design", "custom")); }
    static void setFactory(Context c, boolean on) {
        get(c).edit().putString("design", on ? "factory" : "custom").apply();
    }
    /** The studio behind the watch: 0 is the dark studio, 100 is white, paper tones between. */
    static int background(Context c) { return get(c).getInt("bg", 0); }
    static void setBackground(Context c, int v) {
        get(c).edit().putInt("bg", v).putString("design", "custom").apply();
    }
    /** The watch floating free (full orbit), or the same watch hanging flat as a pendulum. */
    static boolean threeD(Context c) { return get(c).getBoolean("threeD", true); }
    /** Whether the watch holds still in the world as the phone moves. */
    static boolean gyro(Context c) { return get(c).getBoolean("gyro", true); }
    /** Text size of the app screen: a ten-step ladder, 0 to 9; step 4 is the design size. */
    static int textStep(Context c) { return get(c).getInt("text_step", 4); }
    static void setTextStep(Context c, int v) { get(c).edit().putInt("text_step", v).apply(); }
    /** Whether picking the phone up wakes the screen, so the watch shows without the power button. */
    static boolean wakeOnPickup(Context c) { return get(c).getBoolean("wake_pickup", true); }
    /** How long the stand-in lock screen shows the watch before it fades, seconds; -1 = always. */
    static int lockStay(Context c) { return get(c).getInt("lock_stay", 60); }
    static void setLockStay(Context c, int v) { get(c).edit().putInt("lock_stay", v).apply(); }
    /** How long the fade to black takes, seconds. */
    static int lockFade(Context c) { return get(c).getInt("lock_fade", 5); }
    static void setLockFade(Context c, int v) { get(c).edit().putInt("lock_fade", v).apply(); }
    /** What a flick of the dial does on the stand-in lock screen: see Gestures. */
    static String gesture(Context c, String key, String def) { return get(c).getString(key, def); }
    static void setGesture(Context c, String key, String v) { get(c).edit().putString(key, v).apply(); }
    /** The caseback plate as JSON: finish, movement toggles, counter at six. */
    static String plate(Context c) { return get(c).getString("plate", ""); }
    static void setPlate(Context c, String v) {
        get(c).edit().putString("plate", v).putString("design", "custom").apply();
    }
    /** Where the user parked the watch: "zoom,x,y". */
    static String placement(Context c) { return get(c).getString("place", ""); }
    static void setPlacement(Context c, String v) { get(c).edit().putString("place", v).apply(); }
}
