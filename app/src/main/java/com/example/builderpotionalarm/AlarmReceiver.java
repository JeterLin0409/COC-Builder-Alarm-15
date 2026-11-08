package com.example.builderpotionalarm;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public final class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "clash_builder_alarm_v1";

    static void ensureChannel(Context ctx) {
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"建築完工鬧鐘",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("部落衝突建築完工提醒");
        channel.enableVibration(true);
        Uri uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        if(uri!=null) channel.setSound(uri,attrs);
        nm.createNotificationChannel(channel);
    }

    @Override public void onReceive(Context ctx,Intent intent) {
        // Defense in depth: never ring in local quiet hours, including after time-zone changes
        // or when handling a previously-scheduled alarm from an older app version.
        long now=System.currentTimeMillis();
        long completeAt=intent.getLongExtra("complete_at",now);
        if(QuietHoursPolicy.isQuiet(now) || QuietHoursPolicy.isQuiet(completeAt))return;
        String title=intent.getStringExtra("title");
        if(title==null)title="建築完成，回部落衝突安排下一個！";
        int id=intent.getIntExtra("alarm_id",1);
        ensureChannel(ctx);
        Intent ring=new Intent(ctx,RingActivity.class).putExtra("title",title).putExtra("alarm_id",id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(ctx,id,ring,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(ctx,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("COC 建築完成提醒")
                .setContentText(title)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pi)
                .setFullScreenIntent(pi,true)
                .build();
        if(Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) {
            NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(id,notification);
        }
    }
}
