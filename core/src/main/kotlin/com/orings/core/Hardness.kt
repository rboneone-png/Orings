package com.orings.core

/** O 型環硬度（Shore A） */
enum class Hardness(val shoreA: Int) {
    A70(70),
    A80(80),
    A90(90),
    ;

    val label: String get() = "${shoreA}A"
}

/** 壓力單位。計算核心一律使用 MPa。 */
enum class PressureUnit(val label: String, val mpaPerUnit: Double, val decimals: Int) {
    MPA("MPa", 1.0, 3),
    BAR("bar", 0.1, 2),
    PSI("psi", 0.006894757, 1),
    ;

    fun toMpa(value: Double) = value * mpaPerUnit
    fun fromMpa(mpa: Double) = mpa / mpaPerUnit
}

/**
 * 使用條件：工作壓力與擠出間隙。
 *
 * @param pressureMpa 工作壓力（MPa），0 表示無壓或真空
 * @param radialGap 擠出間隙（徑向單邊，mm）：活塞與缸筒、軸桿與殼體之間；端面密封為法蘭間隙
 * @param hardness 選用硬度
 * @param backupRings 擋圈數量：0 無、1 單側（單向壓力）、2 雙側（雙向壓力）
 */
data class Operating(
    val pressureMpa: Double,
    val radialGap: Double,
    val hardness: Hardness,
    val backupRings: Int = 0,
) {
    init {
        require(pressureMpa >= 0) { "壓力不能是負數" }
        require(radialGap >= 0) { "擠出間隙不能是負數" }
        require(backupRings in 0..2) { "擋圈數量只能是 0、1、2" }
    }
}

/**
 * 擠出極限（無擋圈、靜態、室溫附近）。
 *
 * 數值依一般工業 O 型環擠出曲線整理的近似值：硬度越高、間隙越小，能承受的壓力越高。
 * 實際極限受溫度、材質配方、壓力脈動影響很大，正式設計請以廠商擠出曲線為準。
 */
object Extrusion {
    /** 徑向間隙（mm） */
    private val gaps = doubleArrayOf(0.05, 0.10, 0.15, 0.20, 0.25)

    /** 對應各間隙的最大壓力（MPa） */
    private val limits = mapOf(
        Hardness.A70 to doubleArrayOf(10.0, 6.0, 4.0, 2.5, 1.5),
        Hardness.A80 to doubleArrayOf(16.0, 10.0, 7.0, 5.0, 3.5),
        Hardness.A90 to doubleArrayOf(25.0, 16.0, 11.0, 8.0, 5.5),
    )

    /** 加擋圈（PTFE 等）後的壓力上限（MPa） */
    const val BACKUP_LIMIT_MPA = 35.0

    /** 壓力達極限的此比例以上即提醒 */
    const val WARN_RATIO = 0.8

    /**
     * 最大允許壓力（MPa）。
     * 間隙小於 0.05 mm 取 0.05 mm 的值；大於 0.25 mm 依間隙反比遞減。
     */
    fun maxPressure(hardness: Hardness, radialGap: Double, backupRings: Int = 0): Double {
        if (backupRings > 0) return BACKUP_LIMIT_MPA
        val table = limits.getValue(hardness)
        if (radialGap <= gaps.first()) return table.first()
        if (radialGap >= gaps.last()) return table.last() * gaps.last() / radialGap
        val i = gaps.indexOfLast { it <= radialGap }
        val t = (radialGap - gaps[i]) / (gaps[i + 1] - gaps[i])
        return table[i] + (table[i + 1] - table[i]) * t
    }

    /** 硬度推薦結果 */
    data class Recommendation(
        /** 推薦硬度；null 代表加擋圈也撐不住 */
        val hardness: Hardness?,
        val needsBackup: Boolean,
        val maxPressure: Double,
        val reason: String,
    )

    /** 依壓力與間隙推薦硬度：優先選最軟（密封性、安裝性較好）且不需擋圈的硬度 */
    fun recommend(pressureMpa: Double, radialGap: Double): Recommendation {
        for (h in Hardness.entries) {
            val limit = maxPressure(h, radialGap)
            if (pressureMpa <= limit * WARN_RATIO || (h == Hardness.A90 && pressureMpa <= limit)) {
                return Recommendation(
                    h, false, limit,
                    "壓力 ${fmt(pressureMpa)} MPa，間隙 ${fmt(radialGap)} mm 時 ${h.label} 可承受約 ${fmt(limit)} MPa",
                )
            }
        }
        return if (pressureMpa <= BACKUP_LIMIT_MPA) {
            Recommendation(
                Hardness.A90, true, BACKUP_LIMIT_MPA,
                "壓力超過無擋圈的擠出極限，建議 90A 並加擋圈（或縮小間隙）",
            )
        } else {
            Recommendation(
                null, true, BACKUP_LIMIT_MPA,
                "壓力超過 ${fmt(BACKUP_LIMIT_MPA)} MPa，一般 O 型環不適用，請改用其他密封件",
            )
        }
    }

    /** 擋圈厚度（mm，近似值）：線徑 2 mm 以上約 1.4 mm，以下約 1.0 mm */
    fun backupThickness(crossSection: Double): Double = if (crossSection >= 2.0) 1.4 else 1.0

    private fun fmt(v: Double) = "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
}
