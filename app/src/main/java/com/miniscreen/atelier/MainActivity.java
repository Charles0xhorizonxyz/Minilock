package com.miniscreen.atelier;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private final int ink=0xFF090C10, gold=0xFFC9AA7C, muted=0xFF88919C;
    private LinearLayout content;
    private WatchView watch;
    private final TextView[] finishes=new TextView[3];
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(ink);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(18),dp(24),dp(24));
        scroll.addView(content); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30) { android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); }
            return insets;
        });
        TextView masthead=text("M I N I S C R E E N",11,gold); masthead.setGravity(Gravity.CENTER); add(masthead,28);
        TextView title=text("The art of passing time.",28,0xFFF1EDE5); title.setTypeface(Typeface.create("serif",Typeface.NORMAL)); title.setGravity(Gravity.CENTER); add(title,43);
        TextView subtitle=text("A quiet moment. An extraordinary dial.",12,muted); subtitle.setGravity(Gravity.CENTER); add(subtitle,26);
        watch=new WatchView(this);
        int width=getResources().getDisplayMetrics().widthPixels;
        add(watch,Math.min(370,(int)(width/getResources().getDisplayMetrics().density)-36));
        TextView edition=text("01   /   THE ATELIER COLLECTION",10,gold); edition.setLetterSpacing(.15f); edition.setGravity(Gravity.CENTER); add(edition,28);
        TextView finishLabel=text("DIAL FINISH",10,muted); finishLabel.setLetterSpacing(.15f); margin(finishLabel,18,10);
        LinearLayout choices=new LinearLayout(this);
        String[] names={"Midnight","Malachite","Bordeaux"};
        for(int i=0;i<3;i++) {
            final int index=i; TextView button=text(names[i],12,0xFFECE5DC); finishes[i]=button; button.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1); if(i>0)lp.leftMargin=dp(8); choices.addView(button,lp);
            button.setOnClickListener(v->{Prefs.get(this).edit().putInt("finish",index).apply(); updateFinishes(); watch.invalidate();});
            button.setContentDescription(names[i]+" dial finish");
        }
        add(choices,48); updateFinishes();
        toggle("Ambient mode","Dimmed dial · seconds hidden · gentle drift", "ambient",Prefs.ambient(this));
        toggle("Sweeping seconds","A fluid, mechanical rhythm", "sweep",Prefs.sweep(this));
        toggle("Stand-in lock screen","The 3D watch when the screen wakes", "lock",Prefs.lock(this));
        TextView overlay=text("Allow display over other apps   \u2197",12,gold);
        overlay.setPadding(0,dp(14),0,0);
        overlay.setOnClickListener(v->startActivity(new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:"+getPackageName()))));
        add(overlay,-2);
        TextView caution=text("Requires your real screen lock set to None. The phone is then not "+
            "actually locked \u2014 Home escapes this, and no app can stop that. A stopgap until the "+
            "custom build.",11,0xFFC98A8A);
        caution.setLineSpacing(dp(3),1); caution.setPadding(0,dp(8),0,0); add(caution,-2);
        TextView preview=text("Preview screensaver   ↗",14,ink); preview.setGravity(Gravity.CENTER); preview.setBackground(background(gold,gold)); margin(preview,20,0); preview.getLayoutParams().height=dp(52); preview.setOnClickListener(v->startActivity(new Intent(this,PreviewActivity.class)));
        TextView activate=text("Set as Android screensaver",13,gold); activate.setGravity(Gravity.CENTER); add(activate,52);
        activate.setOnClickListener(v->{
            try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
            catch(android.content.ActivityNotFoundException e) { new android.app.AlertDialog.Builder(this).setMessage("This device does not expose Android screensaver settings. You can still use the fullscreen preview.").setPositiveButton("OK",null).show(); }
        });
        TextView note=text("Choose Miniscreen · Atelier in system settings.\nScreensavers run while charging or docked, as supported by your device.",11,muted); note.setGravity(Gravity.CENTER); note.setLineSpacing(dp(3),1); add(note,-2);
    }
    private void updateFinishes() {
        for(int i=0;i<3;i++) { boolean selected=Prefs.finish(this)==i; finishes[i].setBackground(background(selected?0xFF242321:0xFF11161C,selected?gold:0xFF2B323B)); finishes[i].setSelected(selected); }
    }
    private void toggle(String title,String desc,String key,boolean checked) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,dp(15),0,dp(10));
        LinearLayout labels=new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title,14,0xFFE7E4DF)); TextView description=text(desc,10,muted); description.setPadding(0,dp(5),0,0); labels.addView(description);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Switch control=new Switch(this); control.setChecked(checked); control.setContentDescription(title); control.setThumbTintList(android.content.res.ColorStateList.valueOf(gold));
        control.setOnCheckedChangeListener((v,on)->{Prefs.get(this).edit().putBoolean(key,on).apply(); watch.invalidate();});
        row.addView(control,new LinearLayout.LayoutParams(dp(52),dp(48))); add(row,-2);
        View line=new View(this); line.setBackgroundColor(0xFF252A30); content.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable background(int color,int stroke) { GradientDrawable bg=new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(9)); bg.setStroke(dp(1),stroke); return bg; }
    private void add(View v,int height) { content.addView(v,new LinearLayout.LayoutParams(-1,height<0?height:dp(height))); }
    private void margin(View v,int top,int bottom) { LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(top); lp.bottomMargin=dp(bottom); content.addView(v,lp); }
    @Override protected void onResume() {
        super.onResume();
        if(watch!=null)watch.invalidate();
        // only run the watcher while it is both wanted and permitted
        if(Prefs.lock(this) && Settings.canDrawOverlays(this)) LockService.start(this);
        else if(!Prefs.lock(this)) LockService.stop(this);
    }
}
