package com.miniscreen.atelier;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("atelier", Context.MODE_PRIVATE); }
    static int finish(Context c) { return get(c).getInt("finish", 0); }
    static boolean ambient(Context c) { return get(c).getBoolean("ambient", true); }
    static boolean sweep(Context c) { return get(c).getBoolean("sweep", true); }
    static boolean lock(Context c) { return get(c).getBoolean("lock", false); }
}
