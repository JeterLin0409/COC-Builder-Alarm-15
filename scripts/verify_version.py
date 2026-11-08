from pathlib import Path

base=Path(__file__).resolve().parent.parent
app=(base/'app/build.gradle').read_text(encoding='utf-8')
main=(base/'app/src/main/java/com/example/builderpotionalarm/MainActivity.java').read_text(encoding='utf-8')
root=(base/'build.gradle').read_text(encoding='utf-8')
manifest=(base/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
for name, ok in [
    ('versionName 1.6', "versionName '1.6'" in app),
    ('versionCode 7', 'versionCode 7' in app),
    ('app version on screen', '版本 v1.6' in main),
    ('Android Gradle Plugin version', "id 'com.android.application' version '8.6.1'" in root),
    ('early crash handler', 'android:name=".BuilderAlarmApp"' in manifest),
    ('diagnostic UI', '複製診斷資訊' in main),
]:
    print(('PASS' if ok else 'FAIL')+': '+name)
    if not ok: raise SystemExit('Stale or incomplete source: '+name)
print('SOURCE VERSION VERIFIED: v1.6')
