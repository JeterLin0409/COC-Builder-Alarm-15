# COC 建築鬧鐘 — v1.5 （重新整理版）

這是 **Android Studio 原始碼**，不是可直接安裝的 APK。v1.5 主要為了排查「選取遊戲截圖就閃退」與 GitHub 上的舊檔案沒有更新的問題。

## v1.5 可以確認的內容

- 根目錄 `build.gradle`: Android Gradle Plugin 8.6.1；GitHub Actions 使用 Gradle 8.7/JDK 17。
- `app/build.gradle`: **versionCode 6, versionName '1.5'**。
- 主畫面固定顯示 `版本 v1.5 · 確認看到這行才是新版 APK`。
- 內建「複製診斷資訊」按鈕，無須先發生 Java Crash 才能複製。
- `BuilderAlarmApp` 提前安裝 Java 崩潰處理器，OCR 前同步記錄處理狀態，即使沒有 Java exception，也能在下次啟動顯示「上次截圖處理意外中斷」。
- 截圖 decode / OCR 啟動 / callback 都增加防護與錯誤顯示；仍無法保證捕獲原生程式碼 (native) 崩潰。
- 藥水偵測：截圖右側清單旁、`Available` 下方 `59m 57s`；偵測不到預設 0m，但需確認。
- 建築工人藥水 10 倍、最多未來 5 小時、22:00–07:00 不安排（提醒時間或完工時間在區間內均跳過）。

## 最可靠的 GitHub 上傳方法：全新 repository

1. 在 GitHub 開一個新的、空白的 **Private** repository，例：`COC-Builder-Alarm-15`。避免之前的 v1.3 檔案留在 repo 導致混用。
2. 解壓本 ZIP，打開最外層的 `COC_Builder_Alarm_v1.5` 資料夾。用 GitHub `Add file → Upload files` 上傳**裡面的** `app`、`scripts`、`gradle`、`.github` 和所有根目錄檔案。切勿把 ZIP 原封上傳。
3. 在 repo 的 `app/build.gradle` 看到 `versionName '1.5'`，在 `.github/workflows/build-apk.yml` 看到 `Build Android APK v1.5`。若看不到，代表沒有成功上傳。
4. GitHub Actions 點 `Build Android APK v1.5` → `Run workflow`。工作流程會**先檢查版本**，沒有 1.5 就直接報錯。
5. 成功後，從 Artifacts 下載 `COC-Builder-Alarm-v1.5-APK`，解壓取得 `app-debug.apk` 和 `APK_VERSION.txt`。
6. 手機安裝 APK，開啟應看到 `版本 v1.5 · 確認看到這行才是新版 APK`。
7. 導入截圖測試；若崩潰，重新開啟按「複製診斷資訊」貼回 ChatGPT（勿貼含有個資的完整系統報告）。

**注意：** 此環境沒有 Android SDK 或 Gradle，無法完成 APK 編譯或真機測試。請以 GitHub Actions 工作流程的實際結果為準。對於可能的原生 OCR 崩潰，還是需要手機端診斷紀錄。

## 時間算法

建築剩餘工作量為 W 秒、藥水還有 P 秒：

- 若 W ≤ 10P：實際完工倒數 = ceil(W/10)
- 否則：實際完工倒數 = W - 9P

提醒時間 = 完工時間 - 提前提醒分鐘數；只排 5 小時內完工且提醒與完工兩個時間都不在 22:00–06:59 的建築。
