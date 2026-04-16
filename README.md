<p align="center">
<img src="./app/src/main/ic_launcher-playstore.png" height="80"/>
</p>

<div align="center">

[![GitHub stars](https://img.shields.io/github/stars/wenikan/Running-wen?logo=github)](https://github.com/wenikan/Running-wen/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/wenikan/Running-wen?logo=github)](https://github.com/wenikan/Running-wen/network)
[![license](https://img.shields.io/github/license/wenikan/Running-wen)](https://github.com/wenikan/Running-wen/blob/master/LICENSE)
</div>

<div align="center">
飛WEN — 適用於 Android 8.0+ 的免 ROOT 虛擬定位 APP
</div>

## 簡介
&emsp;&emsp;飛WEN 是一款基於 Android 調試 API 與 OSMDroid（OpenStreetMap）實現的 Android 定位修改工具，並同時實現了一個可自由控制移動的搖桿。使用飛WEN，不需要 ROOT 權限即可隨意修改目前位置及模擬移動。

- 地圖引擎：[OSMDroid](https://github.com/osmdroid/osmdroid)（OpenStreetMap，完全開源，無需 API 金鑰）
- 地理搜尋：[Nominatim](https://nominatim.org/)（OpenStreetMap 免費地理編碼服務）
- 原始專案：[GoGoGo](https://github.com/ZCShou/GoGoGo) by ZCShou

## 警告
&emsp;&emsp;**本 APP 僅供個人學習研究 Android 開發使用，嚴禁用於任何遊戲作弊、侵犯他人隱私或其他違法用途。因使用者不當使用所造成的任何後果，由使用者自行承擔。**

## 功能
1. 虛擬定位修改
2. 搖桿控制模擬移動（步行 / 跑步 / 騎車）
3. 歷史定位記錄
4. 地點搜尋（Nominatim）
5. 手動輸入 GPS 座標（WGS84）
6. 衛星圖 / 普通圖切換

## 使用方法
1. 下載 APK 安裝至手機
2. 進入「設定」→「開發人員選項」→「模擬位置 App」→ 選擇「飛WEN」
3. 開啟飛WEN，授予所需權限
4. 點擊地圖上的位置，然後點擊啟動按鈕
5. 長按地圖可查看該位置的詳細地址

## 系統需求
- Android 8.0（API 27）以上
- 需開啟開發人員選項中的「模擬位置 App」

## FAQ

**Q：為何需要開啟「模擬位置 App」？**

A：Android 系統要求必須在開發人員選項中明確指定允許模擬定位的 App，才能讓 APP 覆蓋系統 GPS 位置。

**Q：為何定位不穩定，偶爾會飄回真實位置？**

A：這是 Android 調試 API 的已知限制。建議關閉 Wi-Fi 和行動網路定位，只保留 GPS，可改善穩定性。

**Q：是否支援鴻蒙系統？**

A：理論上可運行，但未經完整測試。

**Q：為何在微信等應用上定位不起作用？**

A：部分應用有額外的定位驗證機制，本工具無法繞過。

## 開發相關

本專案基於 [GoGoGo](https://github.com/ZCShou/GoGoGo) 修改，主要變更：
- 將百度地圖 SDK 替換為 OSMDroid（OpenStreetMap）
- 移除百度定位 SDK，改用 Google Play Location Services
- 地點搜尋與逆地理編碼改用 Nominatim API
- 座標系統統一使用 WGS84（無需 BD09 轉換）
- App 名稱改為「飛WEN」

## 授權
GPL-3.0-only
