# Orings — O型環設計 App

Android App（Kotlin + Jetpack Compose），用來計算 O 型環溝槽的壓縮率、拉伸率、填充率，並推薦溝槽尺寸。

## 專案結構

另有網頁版：`web/index.html`（直接用瀏覽器開啟即可，不需安裝），計算邏輯與 `core` 一致。

| 模組 | 內容 | 建置需求 |
|---|---|---|
| `core/` | 純 Kotlin 計算核心＋單元測試 | 只需 JDK 17+ |
| `app/` | Android Compose 介面 | Android SDK（用 Android Studio 開啟即可） |

沒有 Android SDK 的環境會自動略過 `:app`，只建置 `:core`。

## 下載

- **Android App**：到 [Releases](https://github.com/rboneone-png/Orings/releases) 下載最新的 `orings-vX.Y.Z.apk`
- **網頁版**：https://rboneone-png.github.io/Orings/

發佈新版本：在 Actions 頁面執行「Release APK」並輸入版本號，或推送 `v*` 標籤。

## 開始

1. 用 Android Studio 開啟本資料夾（會自動建立 `local.properties`），按 Run。
2. 只測試計算核心：

```bash
./gradlew :core:test
```

## 計算公式（百分比以小數表示）

- 拉伸率 = (套入直徑 − ID) ÷ ID
- 有效線徑 = CS ÷ √(1 + 拉伸率)（體積守恆近似）
- 壓縮率 = (有效線徑 − 溝槽深度) ÷ 有效線徑
- 填充率 = 有效截面積 ÷ (溝槽深度 × 溝槽寬度)

預設建議範圍（`DesignLimits`）：

| 條件 | 壓縮率 | 拉伸率 | 填充率 |
|---|---|---|---|
| 靜態徑向 | 15–30% | ≤ 5% | ≤ 85% |
| 靜態端面 | 20–30% | ≤ 5% | ≤ 85% |
| 動態 | 10–20% | ≤ 3% | ≤ 85% |

> 以上是一般工業經驗值，實際設計請依材質廠商型錄確認。

## 標準尺寸表

| 標準 | 範圍 | 筆數 |
|---|---|---|
| AS568 | -001 ~ -475，-901 ~ -932（接頭埠口用，不列入推薦） | 369 |
| JIS B2401 P（運動用） | P3 ~ P400 | 122 |
| JIS B2401 G（固定用，不推薦用於動態） | G25 ~ G300 | 56 |

- `SizeCatalog.search()`：依代號或內徑搜尋
- `SizeCatalog.suitableFor()`：輸入密封型式與配合直徑，列出每種線徑最合適的尺寸並附推薦溝槽

> 尺寸數值依標準規則整理，正式使用前請與 SAE AS568 / JIS B2401 原文或廠商型錄核對。

## 單位

計算核心一律用 mm；`LengthUnit`（mm / inch）只負責輸入解析與顯示。App 切換單位時會把已輸入的數值一起換算。

## 材質選擇

`MaterialAdvisor.evaluate(最低溫, 最高溫, 接觸介質, 使用條件)` 會評估 9 種材質：
NBR、HNBR、FKM、EPDM、VMQ、FVMQ、CR、AU、FFKM。

- 排除：溫度超出範圍、介質評等「不建議」、動態使用但材質不耐磨（VMQ、FVMQ）
- 排序：耐受性 → 溫度餘裕 → 相對成本（便宜夠用的優先）

> 溫度與耐受性為一般工業經驗值，正式選用請以廠商型錄為準。

## 待辦

- [ ] ISO 3601、JIS B2401 V / S 系列
- [ ] 尺寸公差
- [ ] 熱膨脹、溶脹修正、溝槽圓角
