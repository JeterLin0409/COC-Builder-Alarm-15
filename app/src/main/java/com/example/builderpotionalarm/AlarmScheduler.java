package com.example.builderpotionalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

final class AlarmScheduler {
    private static final String STORE = "builder_alarms";
    private static final String ENTRIES = "entries";
    private AlarmScheduler() {}

    static boolean canSchedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms();
    }

    static synchronized void schedule(Context ctx, int id, long whenMs, long completionMs, String title) {
        if (QuietHoursPolicy.shouldSuppress(completionMs, whenMs))
            throw new IllegalArgumentException("22:00–07:00 禁止排定鬧鐘");
        scheduleWithoutSaving(ctx,id,whenMs,completionMs,title);
        JSONArray arr = load(ctx);
        JSONArray replacement = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e != null && e.optInt("id") != id && e.optLong("when") > System.currentTimeMillis()) replacement.put(e);
        }
        try {
            JSONObject e = new JSONObject();
            e.put("id", id); e.put("when", whenMs); e.put("complete", completionMs); e.put("title", title);
            replacement.put(e);
        } catch (Exception ignored) { }
        ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(ENTRIES,replacement.toString()).apply();
    }

    static synchronized void cancelAll(Context ctx) {
        AlarmManager am=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        JSONArray arr=load(ctx);
        for (int i=0;i<arr.length();i++) {
            JSONObject e=arr.optJSONObject(i);
            if(e==null)continue;
            int id=e.optInt("id");
            PendingIntent pi=PendingIntent.getBroadcast(ctx,id,new Intent(ctx,AlarmReceiver.class),
                PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
            if(pi!=null) { am.cancel(pi); pi.cancel(); }
        }
        ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().putString(ENTRIES,"[]").apply();
    }

    static void restorePending(Context ctx) {
        if (!canSchedule(ctx)) return;
        JSONArray arr=load(ctx);
        for (int i=0; i<arr.length(); i++) {
            JSONObject e=arr.optJSONObject(i);
            if (e==null || e.optLong("when")<=System.currentTimeMillis()) continue;
            long at=e.optLong("when");
            long complete=e.optLong("complete",at);
            if(!QuietHoursPolicy.shouldSuppress(complete,at))
                scheduleWithoutSaving(ctx,e.optInt("id"),at,complete,e.optString("title","建築完工"));
        }
    }

    private static JSONArray load(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        try { return new JSONArray(prefs.getString(ENTRIES,"[]")); } catch(Exception e) { return new JSONArray(); }
    }

    private static void scheduleWithoutSaving(Context ctx,int id,long whenMs,long completeMs,String title) {
        AlarmManager am=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        Intent receiver=new Intent(ctx,AlarmReceiver.class)
                .putExtra("title",title).putExtra("alarm_id",id).putExtra("complete_at",completeMs);
        PendingIntent trigger=PendingIntent.getBroadcast(ctx,id,receiver,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(ctx,MainActivity.class);
        PendingIntent show=PendingIntent.getActivity(ctx,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(whenMs,show),trigger);
    }
}
