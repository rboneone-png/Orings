package com.orings.core

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * O 型環溝槽計算核心。
 *
 * 公式說明（百分比以 0~1 表示）：
 * - 拉伸率 = (套入直徑 − O 型環內徑) ÷ O 型環內徑
 * - 拉伸後有效線徑 = CS ÷ √(1 + 拉伸率)（截面積依體積守恆近似縮小）
 * - 壓縮率 = (有效線徑 − 溝槽深度) ÷ 有效線徑
 * - 填充率 = 有效截面積 ÷ (溝槽深度 × 有效溝槽寬度)（未扣除溝槽圓角）
 * - 有效溝槽寬度 = 溝槽寬度 − 擋圈數 × 擋圈厚度
 * - 若提供 [Operating]，另檢查擠出極限與硬度是否合適
 */
object GlandCalculator {

    /** 壓縮率超過此值直接判定為錯誤（永久變形、安裝困難） */
    const val HARD_MAX_COMPRESSION = 0.40

    /** 拉伸率超過此值直接判定為錯誤 */
    const val HARD_MAX_STRETCH = 0.08

    /** 活塞密封：O 型環內徑比溝槽底徑大超過此比例即判定裝不穩 */
    const val MAX_LOOSE_PISTON = 0.03

    /** 軸桿密封：O 型環內徑比軸徑大超過此比例即判定會起皺 */
    const val MAX_LOOSE_ROD = 0.05

    /** 推薦設計時的目標填充率 */
    const val TARGET_FILL = 0.75

    fun stretch(ring: ORing, gland: Gland): Double {
        val seatDiameter = when (gland.sealType) {
            SealType.PISTON -> gland.grooveDiameter
            SealType.ROD -> gland.mateDiameter
            SealType.FACE -> gland.grooveDiameter
        }
        return (seatDiameter - ring.innerDiameter) / ring.innerDiameter
    }

    /** 拉伸後的有效線徑；未拉伸（或鬆配）時維持原線徑 */
    fun effectiveCrossSection(crossSection: Double, stretch: Double): Double =
        if (stretch > 0) crossSection / sqrt(1 + stretch) else crossSection

    fun calculate(
        ring: ORing,
        gland: Gland,
        service: Service = Service.STATIC,
        limits: DesignLimits = DesignLimits.defaultFor(gland.sealType, service),
        operating: Operating? = null,
    ): GlandResult {
        val depth = gland.depth
        val stretch = stretch(ring, gland)
        val cs = effectiveCrossSection(ring.crossSection, stretch)
        val compression = (cs - depth) / cs
        val area = PI * cs * cs / 4
        val backups = operating?.backupRings ?: 0
        val effectiveWidth = gland.grooveWidth - backups * Extrusion.backupThickness(ring.crossSection)
        val fill = if (effectiveWidth > 0) area / (depth * effectiveWidth) else Double.POSITIVE_INFINITY
        val extrusionLimit = operating?.let { Extrusion.maxPressure(it.hardness, it.radialGap, it.backupRings) }

        val issues = buildList {
            // 壓縮率
            when {
                compression <= 0 -> add(Issue(Severity.ERROR, "NO_SQUEEZE", "沒有壓縮量，無法密封"))
                compression < limits.minCompression -> add(
                    Issue(Severity.WARNING, "LOW_SQUEEZE", "壓縮率 ${pct(compression)} 低於建議下限 ${pct(limits.minCompression)}")
                )
                compression > HARD_MAX_COMPRESSION -> add(
                    Issue(Severity.ERROR, "OVER_SQUEEZE", "壓縮率 ${pct(compression)} 過高，易永久變形且難以安裝")
                )
                compression > limits.maxCompression -> add(
                    Issue(Severity.WARNING, "HIGH_SQUEEZE", "壓縮率 ${pct(compression)} 高於建議上限 ${pct(limits.maxCompression)}")
                )
            }

            // 拉伸率
            when {
                stretch > HARD_MAX_STRETCH -> add(
                    Issue(Severity.ERROR, "OVER_STRETCH", "拉伸率 ${pct(stretch)} 過高，線徑變細且加速老化")
                )
                stretch > limits.maxStretch -> add(
                    Issue(Severity.WARNING, "HIGH_STRETCH", "拉伸率 ${pct(stretch)} 高於建議上限 ${pct(limits.maxStretch)}")
                )
                stretch < -MAX_LOOSE_PISTON && gland.sealType == SealType.PISTON -> add(
                    Issue(Severity.ERROR, "VERY_LOOSE_RING", "O 型環內徑比溝槽底徑大 ${pct(-stretch)}，裝不穩，請選內徑較小的尺寸")
                )
                stretch < -MAX_LOOSE_ROD && gland.sealType == SealType.ROD -> add(
                    Issue(Severity.ERROR, "VERY_LOOSE_RING", "O 型環內徑比軸徑大 ${pct(-stretch)}，外徑會被溝槽擠壓起皺，請選內徑較小的尺寸")
                )
                stretch < 0 && gland.sealType == SealType.PISTON -> add(
                    Issue(Severity.WARNING, "LOOSE_RING", "O 型環內徑大於溝槽底徑，安裝時可能鬆脫或扭轉")
                )
            }

            // 溝槽寬度與填充率
            if (effectiveWidth < cs) {
                val msg = if (backups > 0) "扣除擋圈後的溝槽寬度小於線徑，O 型環與擋圈放不進去" else "溝槽寬度小於線徑，O 型環放不進去"
                add(Issue(Severity.ERROR, "NARROW_GROOVE", msg))
            }
            when {
                fill > 1.0 -> add(Issue(Severity.ERROR, "OVERFILL", "填充率 ${pct(fill)} 超過 100%，溝槽空間不足"))
                fill > limits.maxFill -> add(
                    Issue(Severity.ERROR, "HIGH_FILL", "填充率 ${pct(fill)} 高於上限 ${pct(limits.maxFill)}，膨脹或熱脹時會擠壞")
                )
            }

            // 壓力、擠出與硬度
            if (operating != null && extrusionLimit != null) {
                val p = operating.pressureMpa
                val h = operating.hardness
                when {
                    p > extrusionLimit -> add(
                        Issue(
                            Severity.ERROR, "EXTRUSION",
                            "壓力 ${num(p)} MPa 超過 ${h.label} 的擠出極限約 ${num(extrusionLimit)} MPa，" +
                                "請提高硬度、縮小間隙或加擋圈",
                        )
                    )
                    p > extrusionLimit * Extrusion.WARN_RATIO -> add(
                        Issue(
                            Severity.WARNING, "NEAR_EXTRUSION",
                            "壓力已達 ${h.label} 擠出極限（約 ${num(extrusionLimit)} MPa）的 ${pct(p / extrusionLimit)}，餘裕不足",
                        )
                    )
                }
                if (h == Hardness.A90 && p < 2.0 && backups == 0) {
                    add(Issue(Severity.WARNING, "HARD_LOW_PRESSURE", "壓力低於 2 MPa 時 90A 密封性較差，建議 70A"))
                }
                if (h == Hardness.A90 && compression > 0.25) {
                    add(Issue(Severity.WARNING, "HARD_HIGH_SQUEEZE", "90A 壓縮率超過 25%，安裝力大且易永久變形"))
                }
            }
        }

        return GlandResult(
            depth = depth,
            stretch = stretch,
            effectiveCrossSection = cs,
            compression = compression,
            fill = fill,
            limits = limits,
            issues = issues,
            effectiveWidth = effectiveWidth,
            extrusionLimit = extrusionLimit,
        )
    }

    /**
     * 依 O 型環與配合尺寸，推薦溝槽尺寸：
     * 壓縮率取建議範圍中間值，填充率取 [TARGET_FILL]。
     *
     * @param mateDiameter PISTON = 缸筒內徑；ROD = 軸桿外徑；FACE 不使用（溝槽內徑取 O 型環內徑）
     */
    fun suggest(
        ring: ORing,
        sealType: SealType,
        service: Service = Service.STATIC,
        mateDiameter: Double = 0.0,
        limits: DesignLimits = DesignLimits.defaultFor(sealType, service),
        operating: Operating? = null,
    ): Gland {
        val targetCompression = (limits.minCompression + limits.maxCompression) / 2
        // 擋圈佔用的寬度要另外加上
        val backupWidth = (operating?.backupRings ?: 0) * Extrusion.backupThickness(ring.crossSection)

        fun dims(stretch: Double): Pair<Double, Double> {
            val cs = effectiveCrossSection(ring.crossSection, stretch)
            val depth = cs * (1 - targetCompression)
            val width = max(PI * cs * cs / 4 / (depth * TARGET_FILL), cs * 1.05)
            return depth to (width + backupWidth)
        }

        return when (sealType) {
            SealType.PISTON -> {
                require(mateDiameter > 0) { "請輸入缸筒內徑" }
                // 溝槽底徑會影響拉伸率，拉伸率又影響有效線徑，迭代幾次收斂
                var grooveDia = mateDiameter - 2 * ring.crossSection
                var depth = 0.0
                var width = 0.0
                repeat(10) {
                    val (d, w) = dims((grooveDia - ring.innerDiameter) / ring.innerDiameter)
                    depth = d
                    width = w
                    grooveDia = mateDiameter - 2 * d
                }
                Gland(sealType, round2(width), round2(mateDiameter - 2 * round2(depth)), mateDiameter)
            }

            SealType.ROD -> {
                require(mateDiameter > 0) { "請輸入軸桿外徑" }
                val (depth, width) = dims((mateDiameter - ring.innerDiameter) / ring.innerDiameter)
                Gland(sealType, round2(width), round2(mateDiameter + 2 * round2(depth)), mateDiameter)
            }

            SealType.FACE -> {
                val (depth, width) = dims(0.0)
                Gland(sealType, round2(width), ring.innerDiameter, faceDepth = round2(depth))
            }
        }
    }

    private fun round2(v: Double) = round(v * 100) / 100

    private fun pct(v: Double) = "%.1f%%".format(java.util.Locale.US, v * 100)

    private fun num(v: Double) = "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
}
