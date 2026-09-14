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
    /** The studio behind the watch: 0 is the dark studio, 100 is white, the rainbow between. */
    static int background(Context c) { return get(c).getInt("bg", 0); }
    static void setBackground(Context c, int v) { get(c).edit().putInt("bg", v).apply(); }
    /** What a flick of the dial does on the stand-in lock screen: see Gestures. */
    static String gesture(Context c, String key, String def) { return get(c).getString(key, def); }
    static void setGesture(Context c, String key, String v) { get(c).edit().putString(key, v).apply(); }
    /** The caseback plate as JSON: finish, movement toggles, counter at six. */
    static String plate(Context c) { return get(c).getString("plate", ""); }
    static void setPlate(Context c, String v) { get(c).edit().putString("plate", v).apply(); }
    /** Where the user parked the watch: "zoom,x,y". */
    static String placement(Context c) { return get(c).getString("place", ""); }
    static void setPlacement(Context c, String v) { get(c).edit().putString("place", v).apply(); }
}
