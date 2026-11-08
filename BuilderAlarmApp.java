package com.example.builderpotionalarm;

import android.app.Application;

/** Install diagnostics before any Activity runs. Never uploads user screenshots. */
public final class BuilderAlarmApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
