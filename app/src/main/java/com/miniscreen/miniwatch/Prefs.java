package com.miniscreen.miniwatch;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("miniwatch", Context.MODE_PRIVATE); }
    static int finish(Context c) { return get(c).getInt("finish", 0); }
    static boolean ambient(Context c) { return get(c).getBoolean("ambient", true); }
    static boolean sweep(Context c) { return get(c).getBoolean("sweep", true); }
    static boolean lock(Context c) { return get(c).getBoolean("lock", false); }
    static boolean card(Context c) { return get(c).getBoolean("card", true); }
    /**
     * "factory": the watch exactly as designed (24k gold, bright dial), saved plate choices
     * kept but not applied. "custom": the caseback as the user left it. A change to the plate
     * makes it custom again. The background and the text under the watch are separate.
     */
    static boolean factory(Context c) { return "factory".equals(get(c).getString("design", "custom")); }
    static void setFactory(Context c, boolean on) {
        get(c).edit().putString("design", on ? "factory" : "custom").apply();
    }
    /** The studio behind the watch: 0 is the dark studio, 100 is white, paper tones between. */
    static int background(Context c) { return get(c).getInt("bg", 0); }
    static void setBackground(Context c, int v) {
        get(c).edit().putInt("bg", v).apply();      // the design is the watch; the paper is yours under either
    }
    /** "orbit": floating free; "hang3d": held by the ring, moving in 3D; "hang2d": a pendulum in the plane. */
    static String motion(Context c) {
        String m = get(c).getString("motion", null);
        if (m != null) return m;
        return get(c).getBoolean("threeD", true) ? "orbit" : "hang2d";   // the older switch
    }
    static void setMotion(Context c, String m) { get(c).edit().putString("motion", m).apply(); }
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
