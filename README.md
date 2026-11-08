# v1.6 修正說明

- 修正 DurationParser 的 `\b?`：ICU 不允許量化 word boundary，造成類別初始化失敗。
- 記錄已攔截的錯誤及完整原因鏈；解析失敗不再被覆寫為成功。
- 解析失敗時清除結果，藥水與建築欄位留空供手動確認。
- 保留原有下方藍色藥水圖示旁的偵測區域；右上方護盾倒數不是藥水。
- 版本 v1.6 / versionCode 7。

## 建置與安裝

把此資料夾的內容（含 `.github`）覆蓋到原 GitHub 專案根目錄，執行 Actions 的 Build Android APK v1.6 工作流程。下載產物內的 app-debug.apk 安裝，開啟後確認畫面寫 v1.6。若簽章不同而無法覆蓋，先保留需要的鬧鐘設定，再決定是否移除舊版。

## 驗證範圍

已在本機重現舊正規表示式的 ICU 編譯錯誤，移除無效語法後所有六個來源 Pattern 都可編譯。純 Java 計算、解析、藥水區域及安靜時段測試通過。此環境沒有 Android SDK，尚未建置 APK 或在 Samsung Android 16 實機跑 OCR。

新增 Android 平台回歸測試，可於裝置／模擬器連線後執行 `gradle connectedDebugAndroidTest`；這項測試尚未執行。GitHub workflow 會執行 JUnit 單元測試及 APK 建置。

---

# COC 建築鬧鐘 — v1.6 （重新整理版）

這是 **Android Studio 原始碼**，不是可直接安裝的 APK。v1.6 主要為了排查「選取遊戲截圖就閃退」與 GitHub 上的舊檔案沒有更新的問題。

## v1.6 可以確認的內容

- 根目錄 `build.gradle`: Android Gradle Plugin 8.6.1；GitHub Actions 使用 Gradle 8.7/JDK 17。
- `app/build.gradle`: **versionCode 7, versionName '1.6'**。
- 主畫面固定顯示 `版本 v1.6 · 確認看到這行才是新版 APK`。
- 內建「複製診斷資訊」按鈕，無須先發生 Java Crash 才能複製。
- `BuilderAlarmApp` 提前安裝 Java 崩潰處理器，OCR 前同步記錄處理狀態，即使沒有 Java exception，也能在下次啟動顯示「上次截圖處理意外中斷」。
- 截圖 decode / OCR 啟動 / callback 都增加防護與錯誤顯示；仍無法保證捕獲原生程式碼 (native) 崩潰。
- 藥水偵測：截圖右側清單旁、`Available` 下方 `59m 57s`；偵測不到預設 0m，但需確認。
- 建築工人藥水 10 倍、最多未來 5 小時、22:00–07:00 不安排（提醒時間或完工時間在區間內均跳過）。

## 最可靠的 GitHub 上傳方法：全新 repository

1. 在 GitHub 開一個新的、空白的 **Private** repository，例：`COC-Builder-Alarm-15`。避免之前的 v1.3 檔案留在 repo 導致混用。
2. 解壓本 ZIP，打開最外層的 `COC_Builder_Alarm_v1.6` 資料夾。用 GitHub `Add file → Upload files` 上傳**裡面的** `app`、`scripts`、`gradle`、`.github` 和所有根目錄檔案。切勿把 ZIP 原封上傳。
3. 在 repo 的 `app/build.gradle` 看到 `versionName '1.6'`，在 `.github/workflows/build-apk.yml` 看到 `Build Android APK v1.6`。若看不到，代表沒有成功上傳。
4. GitHub Actions 點 `Build Android APK v1.6` → `Run workflow`。工作流程會**先檢查版本**，沒有 1.6 就直接報錯。
5. 成功後，從 Artifacts 下載 `COC-Builder-Alarm-v1.6-APK`，解壓取得 `app-debug.apk` 和 `APK_VERSION.txt`。
6. 手機安裝 APK，開啟應看到 `版本 v1.6 · 確認看到這行才是新版 APK`。
7. 導入截圖測試；若崩潰，重新開啟按「複製診斷資訊」貼回 ChatGPT（勿貼含有個資的完整系統報告）。

**注意：** 此環境沒有 Android SDK 或 Gradle，無法完成 APK 編譯或真機測試。請以 GitHub Actions 工作流程的實際結果為準。對於可能的原生 OCR 崩潰，還是需要手機端診斷紀錄。

## 時間算法

建築剩餘工作量為 W 秒、藥水還有 P 秒：

- 若 W ≤ 10P：實際完工倒數 = ceil(W/10)
- 否則：實際完工倒數 = W - 9P

提醒時間 = 完工時間 - 提前提醒分鐘數；只排 5 小時內完工且提醒與完工兩個時間都不在 22:00–06:59 的建築。

## 2026-11-08：GitHub SDK 安裝失敗的 Hotfix

新版 `.github/workflows/build-apk.yml` 使用固定的 `ubuntu-24.04` runner 所預裝的 Android SDK 35 / Build Tools 35.0.0，不執行 `android-actions/setup-android@v3` 或 `sdkmanager`。此修正僅影響 CI 編譯環境，App 仍為 v1.6。

如已建立 Repository，直接編輯 `.github/workflows/build-apk.yml`，將內容換成本 ZIP 的同名檔案，commit 後自動執行。若成功，到 Artifacts 下載 `COC-Builder-Alarm-v1.6-ParserFix-APK`。
