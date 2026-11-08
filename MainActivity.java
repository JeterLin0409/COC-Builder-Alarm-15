package com.example.builderpotionalarm;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.Text;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGE=34;
    private static final int ASK_NOTIFICATION=35;
    private final List<TimerRow> timerRows=new ArrayList<>();
    private final List<PlanItem> currentPlans=new ArrayList<>();
    private LinearLayout timerContainer, resultContainer;
    private ImageView preview;
    private TextView status, resultTitle;
    private Spinner alertLead;
    private Button scheduleAll;
    private int idSeed=10000;
    private long lastAnalyzedAt=0;
    private boolean readingImage=false;
    private final ExecutorService imageExecutor=Executors.newSingleThreadExecutor();
    private int screenshotWidth=0, screenshotHeight=0;
    private static final int NAVY=Color.rgb(22,56,96), BLUE=Color.rgb(18,94,166);

    private static final class TimerRow {
        EditText input;
        Spinner type;
        String raw;
        TimerRow(EditText input, Spinner type, String raw) {this.input=input;this.type=type;this.raw=raw;}
    }
    private static final class RecognizedLine {
        final String raw;
        final int cx, cy;
        RecognizedLine(String raw, int cx, int cy) { this.raw=raw; this.cx=cx; this.cy=cy; }
    }
    private static final class ScreenshotBuild {
        final String label;
        final long seconds;
        final int y;
        ScreenshotBuild(String label, long seconds, int y) {this.label=label;this.seconds=seconds;this.y=y;}
    }
    private static final class BuildInput {
        final long remaining;
        final String name;
        BuildInput(long remaining, String name) {this.remaining=remaining;this.name=name;}
    }
    private static final class PanelRow {
        final List<RecognizedLine> text = new ArrayList<>();
        int y;
        PanelRow(RecognizedLine first) {text.add(first);y=first.cy;}
        void add(RecognizedLine next) {text.add(next);y=(y*(text.size()-1)+next.cy)/text.size();}
    }
    private static final class PlanItem {
        final String label;
        final long realSeconds;
        final long completeAt;
        final long alarmAt;
        PlanItem(String label,long realSeconds,long completeAt,long alarmAt){
            this.label=label;this.realSeconds=realSeconds;this.completeAt=completeAt;this.alarmAt=alarmAt;
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Crash handler is already installed by BuilderAlarmApp before onCreate.
        AlarmReceiver.ensureChannel(this);
        createScreen();
        CrashReporter.showPreviousCrashIfAny(this);
        Uri shared=getSharedUri(getIntent());
        if(shared!=null) loadImage(shared);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri shared=getSharedUri(intent);
        if(shared!=null) loadImage(shared);
    }

    private static Uri getSharedUri(Intent intent) {
        if(intent==null || !Intent.ACTION_SEND.equals(intent.getAction()))return null;
        if(intent.getType()==null || !intent.getType().startsWith("image/"))return null;
        if(Build.VERSION.SDK_INT>=33) return intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    private void createScreen() {
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout main=new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(18),dp(22),dp(18),dp(36));
        main.setBackgroundColor(Color.rgb(247,249,252));
        scroll.addView(main);
        setContentView(scroll);

        TextView title=new TextView(this);
        title.setText("COC 建築鬧鐘");
        title.setTextColor(NAVY);
        title.setTextSize(26);
        title.setTypeface(null,1);
        main.addView(title);
        TextView subtitle=small("截圖 → 自動讀時間 → 5 小時內提醒");
        main.addView(subtitle);
        main.addView(small("版本 v1.6 · 確認看到這行才是新版 APK"));
        Button diagnostics=button("複製診斷資訊（閃退時使用）",false);
        diagnostics.setOnClickListener(v->{CrashReporter.copyDiagnostics(this);msg("已複製本機診斷資訊");});
        main.addView(diagnostics);
        addGap(main,14);

        Button pick=button("📸  選擇遊戲截圖",true);
        pick.setOnClickListener(v->openPicker());
        main.addView(pick);
        status=small("也可以在相簿選擇『分享』→『COC 建築鬧鐘』。\n請在截圖後立即匯入，避免舊截圖造成計時誤差。");
        status.setPadding(0,dp(10),0,dp(8));
        main.addView(status);
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setVisibility(View.GONE);
        LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,dp(180));
        main.addView(preview,imageParams);

        heading(main,"1｜確認辨識到的時間");
        main.addView(small("藥水倒數未出現時自動以 0m（正常速度）計算；若倒數可能有但辨識不清，請手動確認。請核對數字再設鬧鐘。"));
        timerContainer=new LinearLayout(this);
        timerContainer.setOrientation(LinearLayout.VERTICAL);
        main.addView(timerContainer);
        addTimerRow("藥水時間（沒有使用藥水 = 0m）","0m",1);
        addTimerRow("建築時間","",2);
        Button add=button("＋ 新增一個建築時間",false);
        add.setOnClickListener(v->{addTimerRow("手動新增","",2);invalidateResults();});
        main.addView(add);

        heading(main,"2｜提醒設定");
        main.addView(small("只幫未來 5 小時內完工的建築設定提醒。\n安靜時間：每天 22:00–07:00（以手機當地時間為準），若完工時間或提醒時間落在這段期間，直接略過、不設鬧鐘。"));
        alertLead=new Spinner(this);
        String[] leads={"完工時提醒","提前 1 分鐘","提前 2 分鐘（推薦）","提前 5 分鐘"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,leads);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        alertLead.setAdapter(adapter);
        alertLead.setSelection(2);
        main.addView(alertLead);
        Button analyze=button("計算預計完工時間",true);
        analyze.setOnClickListener(v->analyze());
        main.addView(analyze);

        heading(main,"3｜建立鬧鐘");
        resultTitle=small("先匯入截圖並確認時間，再按『計算預計完工時間』。");
        main.addView(resultTitle);
        resultContainer=new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        main.addView(resultContainer);
        scheduleAll=button("⏰ 一鍵建立全部提醒",true);
        scheduleAll.setEnabled(false);
        scheduleAll.setOnClickListener(v->scheduleAll());
        main.addView(scheduleAll);
        Button cancel=button("清除本 App 已建立的鬧鐘",false);
        cancel.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("清除所有本 App 鬧鐘？")
                .setMessage("只會取消此 App 自己排定的鬧鐘，不影響 Samsung 時鐘裡的其他鬧鐘。")
                .setNegativeButton("取消",null)
                .setPositiveButton("清除",(d,w)->{AlarmScheduler.cancelAll(this);Toast.makeText(this,"已清除",Toast.LENGTH_SHORT).show();})
                .show());
        main.addView(cancel);
        addGap(main,12);
        main.addView(small("提醒由本 App 的 Android 精準鬧鐘建立。第一次使用請允許『鬧鐘與提醒』和通知權限。系統的勿擾模式、音量或全螢幕通知權限可能影響響鈴。"));
        main.addView(small("也可以個別按『Samsung 時鐘』，交由手機的時鐘 App 建立；是否跳過確認頁面由時鐘 App 決定。"));
        alertLead.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){invalidateResults();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
    }

    private int dp(float d){return Math.round(d*getResources().getDisplayMetrics().density);}
    private void addGap(LinearLayout parent,int d){View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(1,dp(d)));}
    private TextView small(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(13);t.setTextColor(Color.rgb(94,105,120));return t;}
    private void heading(LinearLayout parent,String s){addGap(parent,20);TextView t=new TextView(this);t.setText(s);t.setTextSize(18);t.setTextColor(NAVY);t.setTypeface(null,1);parent.addView(t);addGap(parent,8);}
    private Button button(String label,boolean filled) {
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(filled?Color.WHITE:BLUE);
        if(filled)b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));
        return b;
    }
    private void addTimerRow(String title,String duration,int selectedRole) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(8),dp(12),dp(10));
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);
        outer.setMargins(0,dp(8),0,dp(4));
        timerContainer.addView(card,outer);
        TextView label=small(title);
        card.addView(label);
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row);
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setHint("例如：2h 30m");
        input.setText(duration);
        row.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
        Spinner kind=new Spinner(this);
        String[] roles={"忽略","藥水","建築"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        kind.setAdapter(adapter);
        kind.setSelection(selectedRole);
        row.addView(kind,new LinearLayout.LayoutParams(dp(95),dp(52)));
        timerRows.add(new TimerRow(input,kind,title));
        input.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){invalidateResults();}
            public void afterTextChanged(android.text.Editable e){}
        });
        kind.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){invalidateResults();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
    }
    private void invalidateResults(){currentPlans.clear();if(resultContainer!=null)resultContainer.removeAllViews();if(scheduleAll!=null)scheduleAll.setEnabled(false);}

    private void openPicker() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent,PICK_IMAGE);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_IMAGE && resultCode==RESULT_OK && data!=null && data.getData()!=null)loadImage(data.getData());
    }
    @Override protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private void loadImage(Uri uri) {
        if(readingImage){msg("正在辨識上一張截圖，請稍後再試。");return;}
        readingImage=true;
        CrashReporter.beginScreenshot(this);
        status.setText("正在讀取截圖……");
        invalidateResults();
        imageExecutor.execute(()->{
            try {
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                try(InputStream stream=getContentResolver().openInputStream(uri)) {
                    if(stream==null)throw new IllegalStateException("無法開啟截圖檔案");
                    BitmapFactory.decodeStream(stream,null,bounds);
                }
                if(bounds.outWidth<=0 || bounds.outHeight<=0)throw new IllegalStateException("圖片格式無法讀取");
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                int sample=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/sample>2000 ||
                     (long)(bounds.outWidth/sample)*(bounds.outHeight/sample)>3500000L)sample*=2;
                options.inSampleSize=sample;
                Bitmap decoded;
                try(InputStream stream=getContentResolver().openInputStream(uri)) {
                    if(stream==null)throw new IllegalStateException("無法重新開啟截圖");
                    decoded=BitmapFactory.decodeStream(stream,null,options);
                }
                if(decoded==null)throw new IllegalStateException("無法解碼圖片");
                runOnUiThread(()->{
                    try {
                        if(isFinishing() || isDestroyed()){
                            readingImage=false;CrashReporter.endScreenshot(this);return;
                        }
                        screenshotWidth=decoded.getWidth();
                        screenshotHeight=decoded.getHeight();
                        preview.setImageBitmap(decoded);
                        preview.setVisibility(View.VISIBLE);
                        startOcr(decoded);
                    } catch(Throwable error) {
                        readingImage=false;
                        CrashReporter.recordHandled(this,"圖片顯示或 OCR 初始化失敗",error);
                        CrashReporter.endScreenshot(this);
                        status.setText("截圖處理失敗："+safeError(error)+"。可手動輸入倒數。");
                    }
                });
            } catch(Throwable e) {
                runOnUiThread(()->{
                    readingImage=false;
                    CrashReporter.recordHandled(this,"讀取圖片失敗",e);
                    CrashReporter.endScreenshot(this);
                    status.setText("截圖讀取失敗："+safeError(e)+"。請使用較小的截圖，或手動輸入倒數。");
                });
            }
        });
    }

    // Only one bundled Latin OCR model is used. The supplied game screenshot
    // contains numeric English timers, so simultaneous Chinese OCR is unnecessary.
    // Sequential scans and explicit recognizer cleanup greatly reduce peak memory.
    private void startOcr(Bitmap bmp) {
        CrashReporter.setStage(this,"初始化文字辨識");
        status.setText("正在辨識建築與藥水時間……");
        final List<RecognizedLine> lines=new ArrayList<>();
        final List<String> errors=new ArrayList<>();
        final TextRecognizer recognizer;
        try {
            recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        } catch(Throwable e) {
            readingImage=false;
            CrashReporter.recordHandled(this,"文字辨識無法啟動",e);
            CrashReporter.endScreenshot(this);
            status.setText("文字辨識無法啟動："+safeError(e)+"。可改用下方欄位手動輸入。");
            return;
        }
        CrashReporter.setStage(this,"辨識完整截圖");
        scanBitmap(recognizer,bmp,0,0,1,lines,errors,
            ()->scanRegion(recognizer,bmp,.555,.490,.705,.630,3,"藥水倒數",lines,errors,
                ()->scanRegion(recognizer,bmp,.695,.075,.975,.485,2,"建築清單",lines,errors,
                    ()->finishOcr(recognizer,lines,errors))));
    }

    private void scanRegion(TextRecognizer recognizer,Bitmap bmp,
            double x0,double y0,double x1,double y1,int scale,String label,
            List<RecognizedLine> lines,List<String> errors,Runnable after) {
        CrashReporter.setStage(this,"辨識"+label);
        int left=Math.max(0,(int)(bmp.getWidth()*x0));
        int top=Math.max(0,(int)(bmp.getHeight()*y0));
        int right=Math.min(bmp.getWidth(),(int)(bmp.getWidth()*x1));
        int bottom=Math.min(bmp.getHeight(),(int)(bmp.getHeight()*y1));
        if(right<=left || bottom<=top){after.run();return;}
        Bitmap crop=null;
        Bitmap scaled=null;
        try {
            crop=Bitmap.createBitmap(bmp,left,top,right-left,bottom-top);
            scaled=Bitmap.createScaledBitmap(crop,(right-left)*scale,(bottom-top)*scale,true);
            if(crop!=scaled)crop.recycle();
            Bitmap cleanup=scaled;
            scanBitmap(recognizer,scaled,left,top,scale,lines,errors,()->{
                cleanup.recycle();
                after.run();
            });
        } catch(Throwable e) {
            CrashReporter.recordHandled(this,"辨識"+label,e);
            errors.add(label+"："+safeError(e));
            if(crop!=null && crop!=bmp && !crop.isRecycled())crop.recycle();
            if(scaled!=null && scaled!=bmp && !scaled.isRecycled())scaled.recycle();
            after.run();
        }
    }

    private void scanBitmap(TextRecognizer recognizer,Bitmap bmp,int left,int top,int scale,
            List<RecognizedLine> lines,List<String> errors,Runnable after) {
        try {
            InputImage input=InputImage.fromBitmap(bmp,0);
            recognizer.process(input)
                .addOnSuccessListener(result->{
                    try {appendLines(result,lines,left,top,scale);}
                    catch(Throwable e){CrashReporter.recordHandled(this,"文字擷取",e);errors.add("文字擷取："+safeError(e));}
                })
                .addOnFailureListener(e->{CrashReporter.recordHandled(this,"文字辨識",e);errors.add("文字辨識："+safeError(e));})
                .addOnCompleteListener(task->{
                    try {after.run();}
                    catch(Throwable e){
                        CrashReporter.recordHandled(this,"後續處理",e);
                        errors.add("後續處理："+safeError(e));
                        finishOcr(recognizer,lines,errors);
                    }
                });
        } catch(Throwable e) {
            CrashReporter.recordHandled(this,"文字辨識啟動",e);
            errors.add("文字辨識啟動："+safeError(e));
            after.run();
        }
    }

    private void finishOcr(TextRecognizer recognizer,
            List<RecognizedLine> lines,List<String> errors) {
        CrashReporter.setStage(this,"顯示辨識結果");
        try {recognizer.close();} catch(Throwable ignored) { }
        readingImage=false;
        CrashReporter.endScreenshot(this);
        try {
            populateRecognized(lines,!errors.isEmpty());
            CrashReporter.setStage(this, errors.isEmpty() ? "辨識已完成，等待使用者操作" : "部分辨識失敗，請核對時間");
            if(!errors.isEmpty())status.append("\n部分辨識失敗："+errors.get(0)+"；請確認每筆倒數。");
        } catch(Throwable e) {
            CrashReporter.recordHandled(this,"辨識結果顯示失敗",e);
            timerRows.clear();timerContainer.removeAllViews();
            addTimerRow("藥水時間（辨識失敗，請手動確認）","",1);
            addTimerRow("建築時間","",2);
            invalidateResults();
            status.setText("圖片已載入，但解析辨識結果時發生錯誤："+safeError(e)+"。可改成手動輸入倒數。");
        }
    }

    private static String safeError(Throwable e) {
        Throwable cause=e;
        for(int depth=0;depth<10 && cause.getCause()!=null && cause.getCause()!=cause;depth++)cause=cause.getCause();
        e=cause;
        String message=e.getMessage();
        String result=e.getClass().getSimpleName()+(message==null?"":"："+message);
        return result.length()>180?result.substring(0,180):result;
    }

    private void appendLines(Text result,List<RecognizedLine> lines) {
        appendLines(result,lines,0,0,1);
    }
    private void appendLines(Text result,List<RecognizedLine> lines,int left,int top,int scale) {
        for(Text.TextBlock block:result.getTextBlocks()) {
            for(Text.Line line:block.getLines()) {
                Rect bounds=line.getBoundingBox();
                if(bounds!=null)lines.add(new RecognizedLine(line.getText(),
                        left+bounds.centerX()/scale,top+bounds.centerY()/scale));
            }
        }
    }

    /** Extract one timer per visible row of the top right-hand Buildings & Heroes panel.
     * OCR duplicate models are grouped by on-screen y-coordinate, not by duration: two
     * separate Mortars both reading "4d 8h" must stay as separate upgrades.
     */
    private List<ScreenshotBuild> extractTopRightBuilds(List<RecognizedLine> lines,int w,int h) {
        List<RecognizedLine> panel=new ArrayList<>();
        for(RecognizedLine line:lines) {
            if(line.cx>=w*.69 && line.cy>=h*.082 && line.cy<=h*.485)
                panel.add(line);
        }
        Collections.sort(panel,(a,b)->Integer.compare(a.cy,b.cy));
        List<PanelRow> rows=new ArrayList<>();
        int tolerance=Math.max(8,(int)(h*.023));
        for(RecognizedLine line:panel) {
            PanelRow match=null;
            for(PanelRow row:rows) if(Math.abs(row.y-line.cy)<=tolerance){match=row;break;}
            if(match==null)rows.add(new PanelRow(line));else match.add(line);
        }
        List<ScreenshotBuild> builds=new ArrayList<>();
        for(PanelRow row:rows) {
            // Merged OCR models can repeat the whole line; use the longest duration
            // present in any single line before trying fragments combined across the row.
            long best=-1;
            String name="";
            List<RecognizedLine> fragments=new ArrayList<>(row.text);
            Collections.sort(fragments,(a,b)->Integer.compare(a.cx,b.cx));
            StringBuilder assembled=new StringBuilder();
            for(RecognizedLine line:fragments){
                long v=DurationParser.parseSeconds(line.raw);
                if(v>best)best=v;
                String words=line.raw.replaceAll("(?iu)\\b\\d{1,3}\\s*(?:days?|hours?|hrs?|mins?|minutes?|secs?|seconds?|[dhms])"," ").trim();
                // Prefer textual building names over isolated timers.
                if(words.length()>name.length() && !words.matches(".*\\d{3,}.*"))name=words;
                // Distinct text across two OCR models is okay: only use combined as fallback.
                if(assembled.indexOf(line.raw)<0)assembled.append(line.raw).append(' ');
            }
            // OCR may split "4h" and "34m" into separate lines on the same row.
            long joined=DurationParser.parseSeconds(assembled.toString());
            if(joined>best)best=joined;
            if(best<=0)continue;
            if(name.isEmpty() || name.length()>35)name="建築 "+(builds.size()+1);
            builds.add(new ScreenshotBuild(name,best,row.y));
        }
        return builds;
    }

    private void populateRecognized(List<RecognizedLine> lines,boolean ocrPartialFailure) {
        // Must match Bitmap coordinates used by ML Kit, not density-scaled ImageView size.
        int w=screenshotWidth, h=screenshotHeight;
        boolean hasPanel=false;
        for(RecognizedLine line:lines) {
            String t=line.raw.toLowerCase(java.util.Locale.ROOT);
            if(t.contains("buildings") || t.contains("suggested upgrades")) {hasPanel=true;break;}
        }
        List<ScreenshotBuild> builds=(w>0&&h>0)?extractTopRightBuilds(lines,w,h):new ArrayList<>();
        List<PotionTimerDetector.Candidate> potionCandidates=new ArrayList<>();
        for(RecognizedLine line:lines){
            potionCandidates.add(new PotionTimerDetector.Candidate(line.raw,line.cx,line.cy));
        }
        PotionTimerDetector.Assessment potion=PotionTimerDetector.assess(potionCandidates,w,h);
        boolean unsurePotion=ocrPartialFailure && potion.state==PotionTimerDetector.State.ABSENT;
        String potionText=potion.state==PotionTimerDetector.State.DETECTED ?
                TimerMath.format(potion.seconds) :
                potion.state==PotionTimerDetector.State.ABSENT && !unsurePotion ? "0m" : "";
        String potionLabel=potion.state==PotionTimerDetector.State.DETECTED ?
                "藥水倒數（已辨識，請確認）" :
                potion.state==PotionTimerDetector.State.ABSENT && !unsurePotion ?
                "未看到藥水倒數 → 預設 0m（正常速度，請確認）" :
                "藥水倒數可能有但辨識不清（請手動輸入）";
        String potionStatus=potion.state==PotionTimerDetector.State.DETECTED ?
                "已辨識藥水倒數："+potionText+"。" :
                potion.state==PotionTimerDetector.State.ABSENT && !unsurePotion ?
                "未偵測到藥水倒數，已自動以 0m（正常速度）計算；若截圖其實有藥水，請手動修正。" :
                "加速倒數疑似存在但辨識不清，請先手動確認並輸入時間，避免設錯鬧鐘。";
        timerRows.clear();
        timerContainer.removeAllViews();
        if((hasPanel || builds.size()>=3) && !builds.isEmpty()) {
            addTimerRow(potionLabel,potionText,1);
            for(ScreenshotBuild build:builds) {
                addTimerRow("辨識到："+build.label,TimerMath.format(build.seconds),2);
            }
            status.setText("已辨識 "+builds.size()+" 個建築／英雄倒數。"+
                    potionStatus+
                    " 請核對數字，再計算。Suggested upgrades 不列入；相同倒數的不同建築分別保留。");
            return;
        }
        List<DurationParser.Candidate> unique=new ArrayList<>();
        for(RecognizedLine line:lines){
            long seconds=DurationParser.parseSeconds(line.raw);
            if(seconds<=0)continue;
            boolean dup=false;
            // Same timer seen by two OCR models at the same coordinates is one entry;
            // distinct buildings with identical times are not discarded.
            for(RecognizedLine prior:lines){
                if(prior==line)break;
                if(Math.abs(prior.cy-line.cy)<=12 && Math.abs(prior.cx-line.cx)<=50 &&
                        seconds==DurationParser.parseSeconds(prior.raw)){dup=true;break;}
            }
            if(!dup)unique.add(new DurationParser.Candidate(line.raw,seconds));
        }
        addTimerRow(potionLabel,potionText,1);
        if(unique.isEmpty()){
            addTimerRow("建築時間","",2);
            status.setText("沒有可靠辨識到建築倒數。"+potionStatus+" 請手動輸入建築剩餘時間。建議截圖包含 Buildings & Heroes 右側清單。");
        }else{
            for(DurationParser.Candidate c:unique)addTimerRow("辨識到：「"+c.label+"」",TimerMath.format(c.seconds),2);
            status.setText("辨識出 "+unique.size()+" 個倒數候選。"+potionStatus+" 此圖片不符合右側建築清單版面，請忽略無關倒數。");
        }
    }

    private int leadMinutes(){int p=alertLead.getSelectedItemPosition();return p==1?1:p==2?2:p==3?5:0;}
    private static String time(long ms){return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(ms));}

    private void analyze() {
        invalidateResults();
        long boost=-1;
        int potions=0;
        List<BuildInput> builds=new ArrayList<>();
        for(TimerRow row:timerRows) {
            int type=row.type.getSelectedItemPosition();
            if(type==0)continue;
            long seconds=DurationParser.parseSeconds(row.input.getText().toString());
            if(seconds<0){msg("請修正時間格式：「"+row.input.getText()+"」");return;}
            if(type==1){potions++;boost=seconds;}
            if(type==2){
                String name=row.raw.startsWith("辨識到：")?row.raw.substring("辨識到：".length()):"建築 "+(builds.size()+1);
                builds.add(new BuildInput(seconds,name));
            }
        }
        if(potions!=1){msg("請選擇『一個』藥水剩餘時間；沒有藥水請使用預設 0m。");return;}
        if(builds.isEmpty()){msg("請至少選擇一個建築倒數。");return;}
        long now=System.currentTimeMillis();
        lastAnalyzedAt=now;
        int i=0,due=0,over=0,quiet=0,immediate=0;
        for(BuildInput build:builds) {
            i++;
            long real=TimerMath.realSecondsUntilFinished(build.remaining,boost);
            if(real==0)continue;
            long complete=now+real*1000;
            long reminder=complete-leadMinutes()*60000L;
            if(real>5*3600){over++;continue;}
            if(QuietHoursPolicy.shouldSuppress(complete,reminder)){
                quiet++;
                TextView skipped=small("🌙 "+build.name+" 於 "+time(complete)+" 完工或提醒落在 22:00–07:00，已略過。");
                skipped.setPadding(0,dp(6),0,dp(6));
                resultContainer.addView(skipped);
                continue;
            }
            if(reminder<=now+3000){
                addImmediateRow(i,TimerMath.format(real));
                immediate++;
                continue;
            }
            PlanItem item=new PlanItem(build.name+"：回村莊安排下一個建築",real,complete,reminder);
            currentPlans.add(item);
            addPlanRow(item);
            due++;
        }
        resultTitle.setText("5 小時內符合條件："+(due+immediate)+" 個；夜間 22:00–07:00 略過："+quiet+" 個；超過 5 小時略過："+over+" 個。"+
            (currentPlans.isEmpty()?"\n沒有需要排定的未來鬧鐘。":"\n按下方按鈕可一次建立本 App 的精準鬧鐘。"));
        scheduleAll.setEnabled(!currentPlans.isEmpty());
    }
    private void addImmediateRow(int i,String text) {
        TextView t=small("⚠ 建築 "+i+" 將在 "+text+" 內完工：現在就該準備回遊戲。");
        t.setTextColor(Color.rgb(175,73,32));
        t.setPadding(0,dp(10),0,dp(10));
        resultContainer.addView(t);
    }
    private void addPlanRow(PlanItem item) {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.WHITE);
        box.setPadding(dp(10),dp(10),dp(10),dp(10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(7),0,dp(6));
        resultContainer.addView(box,lp);
        TextView t=new TextView(this);
        t.setText(item.label+"\n實際剩餘："+TimerMath.format(item.realSeconds)+"\n完工："+time(item.completeAt)+"\n提醒："+time(item.alarmAt));
        t.setTextColor(NAVY);
        t.setTextSize(14);
        box.addView(t);
        Button clock=button("使用 Samsung 時鐘鬧鐘",false);
        clock.setOnClickListener(v->setSamsungClock(item));
        box.addView(clock);
    }
    private void setSamsungClock(PlanItem item){
        if(QuietHoursPolicy.shouldSuppress(item.completeAt,item.alarmAt)){
            msg("已略過：完工或提醒時間落在 22:00–07:00，不設定鬧鐘。");return;
        }
        Calendar cal=Calendar.getInstance();
        cal.setTimeInMillis(item.alarmAt);
        // Samsung Clock accepts hour/minute; round up rather than alerting prematurely.
        if(cal.get(Calendar.SECOND)>0 || cal.get(Calendar.MILLISECOND)>0) cal.add(Calendar.MINUTE,1);
        cal.set(Calendar.SECOND,0);
        cal.set(Calendar.MILLISECOND,0);
        if(QuietHoursPolicy.isQuiet(cal.getTimeInMillis())){
            msg("提醒時間取整到分鐘後落在 22:00–07:00，已略過。");return;
        }
        Intent i=new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR,cal.get(Calendar.HOUR_OF_DAY))
                .putExtra(AlarmClock.EXTRA_MINUTES,cal.get(Calendar.MINUTE))
                .putExtra(AlarmClock.EXTRA_MESSAGE,item.label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI,true);
        try {startActivity(i);}
        catch(ActivityNotFoundException ex){msg("手機找不到支援新增鬧鐘的時鐘 App。");}
        catch(Exception ex){msg("無法開啟 Samsung 時鐘："+ex.getMessage());}
    }
    private void scheduleAll() {
        if(currentPlans.isEmpty()) {msg("沒有可設定的建築提醒");return;}
        if(System.currentTimeMillis()-lastAnalyzedAt>60000){
            msg("距上次計算已超過 1 分鐘，請重新計算再設定鬧鐘。");
            return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},ASK_NOTIFICATION);
            msg("請允許通知，然後再按一次建立鬧鐘。");
            return;
        }
        if(!AlarmScheduler.canSchedule(this)) {
            try {
                Intent request=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:"+getPackageName()));
                startActivity(request);
                msg("請允許『鬧鐘與提醒』，返回本 App 後再按一次。");
            } catch(Exception e){msg("請到系統設定允許此 App 的精準鬧鐘權限。");}
            return;
        }
        try {
            AlarmScheduler.cancelAll(this); // replaces previous batch; avoids duplicate reminders on rescan
            int n=0;
            for(PlanItem item:currentPlans) {
                if(item.alarmAt<=System.currentTimeMillis() ||
                   QuietHoursPolicy.shouldSuppress(item.completeAt,item.alarmAt))continue;
                AlarmScheduler.schedule(this,idSeed++,item.alarmAt,item.completeAt,item.label);
                n++;
            }
            new AlertDialog.Builder(this)
                    .setTitle("已設定 "+n+" 個鬧鐘")
                    .setMessage("只設定未來 5 小時內完工、且完工和提醒時刻都不在 22:00–07:00 的建築，並取代上次由本 App 建立的提醒。\n\n手機鈴聲與提醒顯示受 Android 通知設定影響，首次使用請先做一次短時間測試。")
                    .setPositiveButton("好",null).show();
        }catch(SecurityException e){msg("尚未取得精準鬧鐘權限：請從系統設定允許。");}
    }
    private void msg(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
