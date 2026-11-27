package com.orings.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * O 型環溝槽計算核心。
 *
 * 公式說明（百分比以 0~1 表示）：
 * - 拉伸率（活塞、端面）= (溝槽直徑 − O 型環內徑) ÷ O 型環內徑
 * - 拉伸率（軸桿）= (溝槽中心徑 − O 型環中心徑) ÷ O 型環中心徑
 *   溝槽中心徑 = (軸徑 + 溝槽底徑) ÷ 2；O 型環中心徑 = ID + CS。
 *   軸桿密封的 O 型環裝在殼體溝槽內、外側有溝槽底面頂住，負值代表周向被擠壓
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

    /** 軸桿密封：周向擠壓超過此比例即警告（可能起皺） */
    const val ROD_COMPRESSION_WARN = 0.03

    /** 軸桿密封：周向擠壓超過此比例即判定錯誤 */
    const val ROD_COMPRESSION_ERROR = 0.06

    /** 90A 硬度建議的壓縮率上限（太硬壓太多安裝困難、易永久變形） */
    const val HARD_90A_MAX_COMPRESSION = 0.25

    /** 推薦設計時的目標填充率 */
    const val TARGET_FILL = 0.75

    fun stretch(ring: ORing, gland: Gland): Double = when (gland.sealType) {
        SealType.ROD -> rodStretch(ring, gland.mateDiameter, gland.grooveDiameter)
        SealType.PISTON, SealType.FACE -> (gland.grooveDiameter - ring.innerDiameter) / ring.innerDiameter
    }

    /** 軸桿密封：以中心徑比較，正值 = 被撐開，負值 = 周向被擠壓 */
    fun rodStretch(ring: ORing, rodDiameter: Double, grooveDiameter: Double): Double {
        val glandMean = (rodDiameter + grooveDiameter) / 2
        val ringMean = ring.innerDiameter + ring.crossSection
        return (glandMean - ringMean) / ringMean
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
                stretch < -ROD_COMPRESSION_ERROR && gland.sealType == SealType.ROD -> add(
                    Issue(Severity.ERROR, "ROD_OVER_COMPRESSED", "O 型環比溝槽大，周向被擠壓 ${pct(-stretch)}，裝入後會起皺，請選內徑較小的尺寸")
                )
                stretch < -ROD_COMPRESSION_WARN && gland.sealType == SealType.ROD -> add(
                    Issue(Severity.WARNING, "ROD_COMPRESSED", "O 型環周向被擠壓 ${pct(-stretch)}，高於建議上限 ${pct(ROD_COMPRESSION_WARN)}，可能起皺")
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
                if (h == Hardness.A90 && compression > HARD_90A_MAX_COMPRESSION) {
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
     * 目標拉伸率：活塞取上限的一半（略為撐緊、不易鬆脫）；
     * 軸桿（中心徑）與端面取 0（不撐開也不擠壓）。
     */
    fun targetStretch(sealType: SealType, limits: DesignLimits): Double =
        if (sealType == SealType.PISTON) limits.maxStretch / 2 else 0.0

    /**
     * 依 O 型環推薦整組溝槽尺寸，同時滿足壓縮率、拉伸率、填充率、擋圈與熱膨脹：
     *
     * 1. 配合直徑：[mateDiameter] 為 null（或 ≤ 0）時一併推薦，讓拉伸率等於 [targetStretch]：
     *    活塞 → 溝槽底徑 = ID × (1 + 目標拉伸)，缸筒內徑 = 溝槽底徑 + 2 × 深度；
     *    軸桿 → 溝槽中心徑 = (ID + CS) × (1 + 目標拉伸)，軸徑 = 中心徑 − 深度；
     *    端面 → 溝槽內徑 = ID。
     *    有給值時固定該直徑（PISTON = 缸筒內徑、ROD = 軸桿外徑、FACE = 溝槽內徑），只推薦溝槽。
     * 2. 溝槽深度：讓「最低溫與最高溫壓縮率的平均」等於建議範圍中間值；
     *    沒有溫度時就是組裝溫度的壓縮率等於中間值。
     * 3. 溝槽寬度：組裝時填充率 = [TARGET_FILL]，最高溫時 ≤ [Thermal.HOT_FILL_TARGET]，另加擋圈厚度。
     */
    fun suggest(
        ring: ORing,
        sealType: SealType,
        service: Service = Service.STATIC,
        mateDiameter: Double? = null,
        limits: DesignLimits = DesignLimits.defaultFor(sealType, service),
        operating: Operating? = null,
        thermal: ThermalInput? = null,
    ): Gland {
        val fixed = mateDiameter?.takeIf { it > 0 }
        // 90A 時壓縮率上限改為 25%，避免推薦出 90A 壓太多的溝槽
        val maxC = if (operating?.hardness == Hardness.A90) minOf(limits.maxCompression, HARD_90A_MAX_COMPRESSION)
        else limits.maxCompression
        val tc = (limits.minCompression + maxC) / 2
        // 有溫度時，讓「最低溫與最高溫拉伸率的平均」= 目標值：
        // 拉伸率約為 (1 + 組裝拉伸) × (1 + αm·ΔT) / (1 + αr·ΔT) − 1
        val ts = targetStretch(sealType, limits).let { target ->
            if (thermal == null) target
            else {
                fun k(t: Double): Double {
                    val dT = t - thermal.assemblyC
                    return (1 + thermal.hardwareCte * dT) / (1 + thermal.ringCte * dT)
                }
                (1 + target) * 2 / (k(thermal.tMinC) + k(thermal.tMaxC)) - 1
            }
        }
        // 擋圈佔用的寬度要另外加上
        val backupWidth = (operating?.backupRings ?: 0) * Extrusion.backupThickness(ring.crossSection)
        val id = ring.innerDiameter
        val cs = ring.crossSection

        /** 依深度組出溝槽（寬度先給足，不影響壓縮率） */
        fun build(depth: Double, width: Double = cs * 3 + backupWidth): Gland = when (sealType) {
            SealType.PISTON -> {
                val groove = if (fixed != null) fixed - 2 * depth else id * (1 + ts)
                Gland(sealType, width, groove, fixed ?: (groove + 2 * depth))
            }
            SealType.ROD -> {
                val mean = (id + cs) * (1 + ts)
                val rod = fixed ?: (mean - depth)
                Gland(sealType, width, rod + 2 * depth, rod)
            }
            SealType.FACE -> Gland(sealType, width, fixed ?: id, faceDepth = depth)
        }

        /** 最低溫與最高溫壓縮率的平均；沒有溫度時為組裝溫度的壓縮率 */
        fun meanCompression(depth: Double): Double {
            val g = build(depth)
            if (thermal == null) return calculate(ring, g, service, limits).compression
            val (r1, g1) = Thermal.at(ring, g, thermal, thermal.tMinC)
            val (r2, g2) = Thermal.at(ring, g, thermal, thermal.tMaxC)
            return (calculate(r1, g1, service, limits).compression + calculate(r2, g2, service, limits).compression) / 2
        }

        if (sealType == SealType.PISTON && fixed != null) require(fixed > cs) { "缸筒內徑太小" }

        // 壓縮率隨深度遞減，用二分法找出平均壓縮率 = 中間值的深度
        var lo = cs * 0.3
        // 固定缸筒內徑時，溝槽底徑不能小於 0
        var hi = if (sealType == SealType.PISTON && fixed != null) minOf(cs * 1.2, fixed * 0.45) else cs * 1.2
        repeat(60) {
            val mid = (lo + hi) / 2
            if (meanCompression(mid) > tc) lo = mid else hi = mid
        }
        val depth = round2((lo + hi) / 2)

        // 尺寸取到 0.01 mm
        val raw = build(depth)
        val rounded = when (sealType) {
            SealType.PISTON -> {
                val groove = round2(raw.grooveDiameter)
                Gland(sealType, raw.grooveWidth, groove, fixed ?: round2(groove + 2 * depth))
            }
            SealType.ROD -> {
                val rod = fixed ?: round2(raw.mateDiameter)
                Gland(sealType, raw.grooveWidth, round2(rod + 2 * depth), rod)
            }
            SealType.FACE -> Gland(sealType, raw.grooveWidth, fixed ?: round2(raw.grooveDiameter), faceDepth = depth)
        }

        // 寬度：組裝時填充率 = 目標值（以組裝溫度的有效線徑計算）
        val room = calculate(ring, rounded, service, limits)
        val effCs = room.effectiveCrossSection
        val width = max(PI * effCs * effCs / 4 / (room.depth * TARGET_FILL), effCs * 1.05) + backupWidth
        var gland = rounded.copy(grooveWidth = round2(width))

        // 高溫時 O 型環膨脹比溝槽多，填充率上升：用實際高溫計算的填充率回推所需寬度
        if (thermal != null && thermal.tMaxC > thermal.assemblyC) {
            val (hotRing, hotGland) = Thermal.at(ring, gland, thermal, thermal.tMaxC)
            val hotFill = calculate(hotRing, hotGland, service, limits, operating).fill
            if (hotFill > Thermal.HOT_FILL_TARGET) {
                val w = (gland.grooveWidth - backupWidth) * hotFill / Thermal.HOT_FILL_TARGET + backupWidth
                gland = gland.copy(grooveWidth = ceil(w * 100) / 100)
            }
        }
        return gland
    }

    private fun round2(v: Double) = round(v * 100) / 100

    private fun pct(v: Double) = "%.1f%%".format(java.util.Locale.US, v * 100)

    private fun num(v: Double) = "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
}
