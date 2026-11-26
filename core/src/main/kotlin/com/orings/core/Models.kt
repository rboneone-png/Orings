package com.orings.core

import kotlin.math.PI

/** O 型環尺寸，單位 mm。 */
data class ORing(
    /** 內徑 ID */
    val innerDiameter: Double,
    /** 線徑（截面直徑）CS */
    val crossSection: Double,
) {
    init {
        require(innerDiameter > 0) { "內徑必須大於 0" }
        require(crossSection > 0) { "線徑必須大於 0" }
    }

    val outerDiameter: Double get() = innerDiameter + 2 * crossSection

    /** 截面積（未拉伸） */
    val crossSectionArea: Double get() = PI * crossSection * crossSection / 4
}

/** 密封型式 */
enum class SealType(val label: String) {
    /** 活塞密封：溝槽在軸（活塞）上，O 型環外側壓在缸筒內壁 */
    PISTON("活塞（外徑）密封"),

    /** 軸桿密封：溝槽在殼體孔內，O 型環內側壓在軸桿上 */
    ROD("軸桿（內徑）密封"),

    /** 端面密封：溝槽在平面上，軸向壓縮 */
    FACE("端面（軸向）密封"),
}

/** 使用條件 */
enum class Service(val label: String) {
    STATIC("靜態"),
    DYNAMIC("動態（往復）"),
}

/**
 * 溝槽幾何，單位 mm。
 *
 * - [SealType.PISTON]：[mateDiameter] = 缸筒內徑，[grooveDiameter] = 溝槽底徑（活塞上）
 * - [SealType.ROD]：[mateDiameter] = 軸桿外徑，[grooveDiameter] = 溝槽底徑（殼體內）
 * - [SealType.FACE]：[grooveDiameter] = 溝槽內徑（O 型環套入的位置），[mateDiameter] 不使用；
 *   壓縮深度直接用 [faceDepth]
 */
data class Gland(
    val sealType: SealType,
    val grooveWidth: Double,
    val grooveDiameter: Double,
    val mateDiameter: Double = 0.0,
    val faceDepth: Double = 0.0,
) {
    init {
        require(grooveWidth > 0) { "溝槽寬度必須大於 0" }
        require(grooveDiameter > 0) { "溝槽直徑必須大於 0" }
        when (sealType) {
            SealType.PISTON -> require(mateDiameter > grooveDiameter) { "缸筒內徑必須大於溝槽底徑" }
            SealType.ROD -> require(grooveDiameter > mateDiameter && mateDiameter > 0) { "溝槽底徑必須大於軸桿外徑" }
            SealType.FACE -> require(faceDepth > 0) { "端面溝槽深度必須大於 0" }
        }
    }

    /** 溝槽深度（含配合間隙的「腺體深度」gland depth） */
    val depth: Double
        get() = when (sealType) {
            SealType.PISTON -> (mateDiameter - grooveDiameter) / 2
            SealType.ROD -> (grooveDiameter - mateDiameter) / 2
            SealType.FACE -> faceDepth
        }
}

/** 設計建議範圍（百分比以 0~1 表示） */
data class DesignLimits(
    val minCompression: Double,
    val maxCompression: Double,
    val maxStretch: Double = 0.05,
    val maxFill: Double = 0.85,
) {
    companion object {
        /** 一般工業經驗值；實際請依材質廠商型錄調整 */
        fun defaultFor(sealType: SealType, service: Service): DesignLimits = when {
            service == Service.DYNAMIC -> DesignLimits(0.10, 0.20, maxStretch = 0.03)
            sealType == SealType.FACE -> DesignLimits(0.20, 0.30)
            else -> DesignLimits(0.15, 0.30)
        }
    }
}

enum class Severity { WARNING, ERROR }

data class Issue(val severity: Severity, val code: String, val message: String)

/** 計算結果（百分比以 0~1 表示，長度單位 mm） */
data class GlandResult(
    val depth: Double,
    /** 拉伸率；負值代表 O 型環比套入直徑大（鬆或被周向壓縮） */
    val stretch: Double,
    /** 拉伸後的有效線徑 */
    val effectiveCrossSection: Double,
    val compression: Double,
    val fill: Double,
    val limits: DesignLimits,
    val issues: List<Issue>,
) {
    val isOk: Boolean get() = issues.none { it.severity == Severity.ERROR }
}
