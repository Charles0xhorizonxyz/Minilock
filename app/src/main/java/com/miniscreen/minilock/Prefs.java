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
    /** Where the user parked the watch: "zoom,x,y". */
    static String placement(Context c) { return get(c).getString("place", ""); }
    static void setPlacement(Context c, String v) { get(c).edit().putString("place", v).apply(); }
}
