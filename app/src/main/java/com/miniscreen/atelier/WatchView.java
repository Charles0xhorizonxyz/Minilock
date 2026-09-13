package com.miniscreen.atelier;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Resolution independent, locally rendered dial. All complications use device time. */
public class WatchView extends View {
    private final Paint p = new Paint(3);
    private final Path path = new Path();
    private boolean exhibition;
    private int gold, light, dial;
    private final Shader[] bezels = new Shader[3], dials = new Shader[3];
    private static final String[] DAYS={"M","T","W","T","F","S","S"}, DATES={"1","5","10","15","20","25"}, HOURS={"0","4","8","12","16","20"};
    private final int[][] finishes = {
        {0xFFC9AA7C, 0xFFF1DEC0, 0xFF142B40},
        {0xFFBFC9C9, 0xFFF1F4EC, 0xFF16372E},
        {0xFFD3A28D, 0xFFFFE0CA, 0xFF302225}
    };
    public WatchView(Context c) {
        super(c); setContentDescription("Atelier analog clock with day, date and 24-hour subdials");
        for(int i=0;i<3;i++) {
            bezels[i]=new LinearGradient(-260,-280,220,290,new int[]{0xFF302E2B,finishes[i][1],0xFF685A46,finishes[i][0],0xFF24282B},null,Shader.TileMode.CLAMP);
            dials[i]=new RadialGradient(-80,-100,480,new int[]{finishes[i][2],0xFF080E17},null,Shader.TileMode.CLAMP);
        }
    }
    public void setExhibition(boolean value) { exhibition = value; }
    private void fill(int color) { p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); }
    private void circle(Canvas c, float x, float y, float r, int color) { fill(color); c.drawCircle(x,y,r,p); }
    private void ring(Canvas c, float x, float y, float r, int color, float width) {
        fill(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); c.drawCircle(x,y,r,p); p.setStyle(Paint.Style.FILL);
    }
    private void label(Canvas c, String text, float x, float y, float size, int color, boolean serif) {
        fill(color); p.setTypeface(Typeface.create(serif ? "serif" : "sans-serif", Typeface.NORMAL));
        p.setTextSize(size); p.setTextAlign(Paint.Align.CENTER); c.drawText(text,x,y,p);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        ZonedDateTime now = ZonedDateTime.now();
        int finish=Math.max(0, Math.min(2, Prefs.finish(getContext())));
        int[] colors = finishes[finish];
        gold=colors[0]; light=colors[1]; dial=colors[2];
        boolean dim = exhibition && Prefs.ambient(getContext());
        c.drawColor(exhibition ? Color.BLACK : 0xFF090C10);
        float size = Math.min(getWidth()*.92f, getHeight()*(exhibition ? .74f : .98f));
        float driftX = exhibition ? (float)Math.sin(System.currentTimeMillis()/180000.0)*getWidth()*.018f : 0;
        float driftY = exhibition ? (float)Math.cos(System.currentTimeMillis()/210000.0)*getHeight()*.014f : 0;
        c.save(); c.translate(getWidth()/2f+driftX,getHeight()/2f+driftY); c.scale(size/600f,size/600f);
        if(dim) c.saveLayerAlpha(-330,-420,330,420,125);
        // A polished bezel surrounds a brushed, blue-black chapter ring.
        fill(gold); p.setShader(bezels[finish]);
        c.drawCircle(0,0,291,p);
        circle(c,0,0,282,0xFF11171D); ring(c,0,0,279,gold,1);
        fill(dial); p.setShader(dials[finish]); c.drawCircle(0,0,270,p);
        // Fine radial guilloche, kept inside the dial.
        c.save(); path.reset(); path.addCircle(0,0,246,Path.Direction.CW); c.clipPath(path);
        fill(0x0ECCDDED); p.setStrokeWidth(.6f);
        for(int i=0;i<360;i++) { c.save(); c.rotate(i); c.drawLine(0,0,45,-260,p); c.restore(); }
        c.restore();
        ring(c,0,0,246,0xFF53606A,.7f); ring(c,0,0,239,0xFF333E49,.6f);
        for(int i=0;i<60;i++) {
            c.save(); c.rotate(i*6); fill(i%5==0 ? gold : 0xFF80909B);
            p.setStrokeWidth(i%5==0 ? 2 : 1); c.drawLine(0,-254,0,i%5==0 ? -264 : -259,p); c.restore();
        }
        for(int i=1;i<=12;i++) {
            c.save(); c.rotate(i*30);
            fill(gold); c.drawRoundRect(-5,-229,5,-201,1.5f,1.5f,p);
            fill(light); c.drawRect(-4,-228,0,-202,p);
            if(i==12) { fill(gold); c.drawRect(-12,-229,-8,-201,p); }
            c.restore();
        }
        label(c,"A T E L I E R",0,-143,23,light,true);
        label(c,"M I N I S C R E E N",0,-121,8,gold,false);
        subdial(c,-112,8,"DAY",(now.getDayOfWeek().getValue()-1)/7f*360,DAYS);
        subdial(c,112,8,"DATE",(now.getDayOfMonth()-1)/31f*360,DATES);
        subdial(c,0,119,"24 H",(now.getHour()+now.getMinute()/60f)*15,HOURS);
        label(c,"LOCAL TIME",0,-65,9,0xFF9FAFB9,false);
        double seconds = now.getSecond()+(Prefs.sweep(getContext())&&!dim ? now.getNano()/1e9 : 0);
        hand(c,(float)((now.getHour()%12+now.getMinute()/60.0)*30),135,8);
        hand(c,(float)((now.getMinute()+seconds/60)*6),195,5);
        if(!dim) {
            c.save(); c.rotate((float)seconds*6); fill(gold); p.setStrokeWidth(1.5f); c.drawLine(0,38,0,-223,p); ring(c,0,29,7,gold,2); c.restore();
        }
        circle(c,0,0,11,0xFF070C12); circle(c,0,0,7,gold); circle(c,-1,-1,3,light);
        label(c,"E D I T I O N   0 1",0,187,8,0xFF97A5AE,false);
        if(exhibition) {
            label(c,now.format(DateTimeFormatter.ofPattern("EEEE  ·  d MMMM",Locale.getDefault())).toUpperCase(Locale.getDefault()),0,345,13,gold,false);
            label(c,now.getZone().getId().replace('_',' '),0,370,10,0xFF86939F,false);
        }
        if(dim) c.restore();
        c.restore();
        if(isShown() && getWindowVisibility()==VISIBLE) postInvalidateDelayed(dim ? 1000 : Prefs.sweep(getContext()) ? 33 : 1000);
    }
    private void subdial(Canvas c,float x,float y,String title,float angle,String[] labels) {
        circle(c,x,y,62,0xFF0B1520); ring(c,x,y,62,0xFF786D5E,1); ring(c,x,y,57,0xFF293642,1);
        for(int i=0;i<30;i++) {
            double a=i*Math.PI/15; fill(0xFF71808C); p.setStrokeWidth(.7f);
            c.drawLine(x+(float)Math.sin(a)*52,y-(float)Math.cos(a)*52,x+(float)Math.sin(a)*55,y-(float)Math.cos(a)*55,p);
        }
        for(int i=0;i<labels.length;i++) {
            double a=title.equals("DATE") ? (Integer.parseInt(labels[i])-1)*2*Math.PI/31 : i*2*Math.PI/labels.length;
            label(c,labels[i],x+(float)Math.sin(a)*42,y-(float)Math.cos(a)*42+3,8,gold,false);
        }
        label(c,title,x,y+23,7,0xFF9DA7AC,false);
        c.save(); c.translate(x,y); c.rotate(angle); fill(light); p.setStrokeWidth(1.8f); c.drawLine(0,7,0,-33,p); circle(c,0,0,3,gold); c.restore();
    }
    private void hand(Canvas c,float angle,float length,float width) {
        c.save(); c.rotate(angle);
        path.reset(); path.moveTo(-width,18); path.lineTo(-width,-length+24); path.lineTo(0,-length); path.lineTo(width,-length+24); path.lineTo(width,18); path.close();
        fill(gold); p.setShadowLayer(4,3,4,0xAA000000); c.drawPath(path,p); p.clearShadowLayer();
        path.reset(); path.moveTo(0,12); path.lineTo(0,-length+4); path.lineTo(width-1,-length+24); path.lineTo(width-1,12); path.close(); fill(light); c.drawPath(path,p);
        c.restore();
    }
}
