package com.example.builderpotionalarm;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class RingActivity extends Activity {
    private Ringtone ringtone;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(35,35,35,35);
        root.setBackgroundColor(Color.rgb(15,49,87));
        TextView title=new TextView(this);
        title.setText("⏰ 建築工人要空閒了！");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView desc=new TextView(this);
        desc.setText(getIntent().getStringExtra("title"));
        desc.setTextColor(Color.WHITE);
        desc.setTextSize(17);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(5,35,5,35);
        root.addView(desc);
        Button dismiss=new Button(this);
        dismiss.setText("關閉鬧鐘");
        dismiss.setOnClickListener(v->finish());
        root.addView(dismiss);
        Button game=new Button(this);
        game.setText("打開部落衝突");
        game.setOnClickListener(v->{
            Intent open=getPackageManager().getLaunchIntentForPackage("com.supercell.clashofclans");
            if(open!=null)startActivity(open);
            finish();
        });
        root.addView(game);
        setContentView(root);
        try {
            ringtone=RingtoneManager.getRingtone(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if(ringtone!=null) { if(android.os.Build.VERSION.SDK_INT>=28)ringtone.setLooping(true); ringtone.play(); }
        } catch(Exception ignored) { }
    }
    @Override protected void onDestroy() {
        if(ringtone!=null && ringtone.isPlaying())ringtone.stop();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(getIntent().getIntExtra("alarm_id",1));
        super.onDestroy();
    }
}
