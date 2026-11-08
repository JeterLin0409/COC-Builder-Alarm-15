package com.example.builderpotionalarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/** Local diagnostics: no screenshot, account data or logs are transmitted. */
final class CrashReporter {
    static final String VERSION = "1.6";
    private static final String PREF = "local_crash_report";
    private CrashReporter() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static void setStage(Context context, String stage) {
        prefs(context).edit().putString("stage", stage).apply();
    }

    /** Persist synchronously; survives process death during native OCR operations. */
    static void beginScreenshot(Context ctx) {
        prefs(ctx).edit().putString("stage", "開始載入截圖")
                .putBoolean("processing", true).remove("handled").commit();
    }

    static void endScreenshot(Context ctx) {
        prefs(ctx).edit().putBoolean("processing", false).apply();
    }

    /** Keep caught errors too: they never reach the uncaught exception handler. */
    static void recordHandled(Context ctx, String stage, Throwable error) {
        try {
            String report = "COC v" + VERSION + "\nStage: " + stage + "\n\n"
                    + Log.getStackTraceString(error);
            prefs(ctx).edit().putString("stage", stage)
                    .putString("handled", report).commit();
        } catch (Throwable ignored) { }
    }

    static void install(Context context) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        if (previous instanceof Reporter) return;
        Thread.setDefaultUncaughtExceptionHandler(new Reporter(context.getApplicationContext(), previous));
    }

    private static final class Reporter implements Thread.UncaughtExceptionHandler {
        private final Context context;
        private final Thread.UncaughtExceptionHandler previous;
        Reporter(Context c, Thread.UncaughtExceptionHandler p) { context=c; previous=p; }
        @Override public void uncaughtException(Thread thread, Throwable error) {
            try {
                SharedPreferences sp = prefs(context);
                String report = "COC v"+VERSION+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+
                        ", Android "+Build.VERSION.RELEASE+"\nStage: "+
                        sp.getString("stage", "unknown")+"\n\n"+Log.getStackTraceString(error);
                if(report.length()>14000) report=report.substring(0,14000);
                sp.edit().putString("crash", report).commit();
            } catch(Throwable ignored) { }
            if(previous!=null) previous.uncaughtException(thread,error);
            else System.exit(1);
        }
    }

    static String diagnostics(Context ctx) {
        SharedPreferences sp=prefs(ctx);
        String text="COC Builder Alarm v"+VERSION+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+
                "\nAndroid: "+Build.VERSION.RELEASE+
                "\nLast stage: "+sp.getString("stage", "unknown")+
                "\nProcessing interrupted: "+sp.getBoolean("processing", false);
        String handled=sp.getString("handled",null);
        if(handled!=null) text+="\n\nLast caught error:\n"+handled;
        String crash=sp.getString("crash",null);
        if(crash!=null) text+="\n\nLast Java crash:\n"+crash;
        else if(handled==null) text+="\n\nNo recorded exception. This alone does not identify the cause of a failure.";
        return text;
    }

    private static void copy(Activity activity, String report) {
        ClipboardManager cb=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if(cb!=null) cb.setPrimaryClip(ClipData.newPlainText("COC diagnostic",report));
    }

    static void copyDiagnostics(Activity activity) { copy(activity,diagnostics(activity)); }

    static void showPreviousCrashIfAny(Activity activity) {
        SharedPreferences sp=prefs(activity);
        boolean interrupted=sp.getBoolean("processing",false);
        String report=sp.getString("crash",null);
        if(!interrupted && report==null)return;
        String details=diagnostics(activity);
        // Clear after snapshotting the report so a stale notice doesn't show each launch.
        sp.edit().remove("crash").putBoolean("processing",false).commit();
        new AlertDialog.Builder(activity)
            .setTitle("上次截圖處理意外中斷")
            .setMessage("請按『複製診斷』貼給 ChatGPT。即使沒有 Java 堆疊，階段資訊仍能幫助定位問題。")
            .setNegativeButton("關閉",null)
            .setPositiveButton("複製診斷",(dialog,which)->copy(activity,details))
            .show();
    }
}
