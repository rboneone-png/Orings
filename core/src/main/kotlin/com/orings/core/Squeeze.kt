package com.orings.core

import java.util.Locale

/**
 * 預壓量（壓縮量）計算與判定，含公差最差情況。
 *
 * - 預壓量（mm）= 有效線徑 − 溝槽深度；預壓率 = 預壓量 ÷ 有效線徑
 * - 最小預壓：線徑取下限、溝槽深度取上限（+ 偏心）
 * - 最大預壓：線徑取上限、溝槽深度取下限（− 偏心）
 * - 偏心：徑向密封時活塞（或軸）偏到一側，溝槽深度會再變化一個擠出間隙
 *
 * 判定門檻為一般工業經驗值。
 */
object Squeeze {

    /** 最差情況最小預壓量（mm）低於此值即警告：小線徑對公差特別敏感 */
    const val MIN_SQUEEZE_MM = 0.10

    /** 最差情況可比建議範圍寬多少（上下各 5 個百分點） */
    const val WORST_CASE_MARGIN = 0.05

    /**
     * 常用線徑的公差（± mm）：AS568（英吋規格換算）與 JIS B2401。
     * 其他線徑依相鄰規格內插。
     */
    private val knownTolerances = listOf(
        1.78 to 0.076, // AS568 .070 ±.003
        1.90 to 0.08, // JIS
        2.40 to 0.09, // JIS
        2.62 to 0.076, // AS568 .103 ±.003
        3.10 to 0.10, // JIS
        3.50 to 0.10, // JIS
        3.53 to 0.102, // AS568 .139 ±.004
        5.33 to 0.127, // AS568 .210 ±.005
        5.70 to 0.13, // JIS
        6.99 to 0.152, // AS568 .275 ±.006
        8.40 to 0.15, // JIS
    )

    /** 線徑公差（± mm）：與常用規格差 0.03 mm 以內直接採用，否則內插 */
    fun csTolerance(cs: Double): Double {
        knownTolerances.firstOrNull { kotlin.math.abs(it.first - cs) <= 0.03 }?.let { return it.second }
        val sorted = knownTolerances.sortedBy { it.first }
        if (cs <= sorted.first().first) return sorted.first().second
        if (cs >= sorted.last().first) return sorted.last().second
        val i = sorted.indexOfLast { it.first <= cs }
        val (x0, y0) = sorted[i]
        val (x1, y1) = sorted[i + 1]
        return y0 + (y1 - y0) * (cs - x0) / (x1 - x0)
    }

    data class Result(
        val nominalMm: Double,
        val nominalPct: Double,
        val minMm: Double,
        val minPct: Double,
        val maxMm: Double,
        val maxPct: Double,
        /** 採用的線徑公差（±，已換算為拉伸後） */
        val csTolerance: Double,
        val depthTolerance: Double,
        val eccentricity: Double,
        val limits: DesignLimits,
        val issues: List<Issue>,
    ) {
        val isOk: Boolean get() = issues.none { it.severity == Severity.ERROR }
    }

    /**
     * @param csTolerance 線徑公差（±mm）；null = 依線徑自動
     * @param depthTolerance 溝槽深度公差（±mm），由配合直徑與溝槽直徑公差合成
     * @param eccentricity 偏心量（mm），通常取擠出間隙；端面密封不適用
     */
    fun analyze(
        ring: ORing,
        gland: Gland,
        service: Service = Service.STATIC,
        csTolerance: Double? = null,
        depthTolerance: Double = 0.05,
        eccentricity: Double = 0.0,
        limits: DesignLimits = DesignLimits.defaultFor(gland.sealType, service),
    ): Result {
        require(depthTolerance >= 0) { "溝槽深度公差不能是負數" }
        require(eccentricity >= 0) { "偏心量不能是負數" }
        val base = GlandCalculator.calculate(ring, gland, service, limits)
        // 拉伸會讓線徑變細，公差也依同比例縮小
        val k = base.effectiveCrossSection / ring.crossSection
        val cs = base.effectiveCrossSection
        val tol = (csTolerance ?: csTolerance(ring.crossSection)).also { require(it >= 0) { "線徑公差不能是負數" } } * k
        val ecc = if (gland.sealType == SealType.FACE) 0.0 else eccentricity
        val d = base.depth

        val nominal = cs - d
        val minMm = (cs - tol) - (d + depthTolerance + ecc)
        val maxMm = (cs + tol) - (d - depthTolerance - ecc)
        val minPct = minMm / (cs - tol)
        val maxPct = maxMm / (cs + tol)

        val issues = buildList {
            val floor = limits.minCompression - WORST_CASE_MARGIN
            val ceiling = limits.maxCompression + WORST_CASE_MARGIN
            when {
                minMm <= 0 -> add(Issue(Severity.ERROR, "SQUEEZE_NONE", "公差最差時沒有預壓量（${mm(minMm)}），會洩漏"))
                minPct < floor -> add(
                    Issue(Severity.WARNING, "SQUEEZE_LOW", "公差最差時預壓率只有 ${pct(minPct)}，低於 ${pct(floor)}，密封可能不足"),
                )
            }
            if (minMm > 0 && minMm < MIN_SQUEEZE_MM) {
                add(Issue(Severity.WARNING, "SQUEEZE_SMALL", "公差最差時預壓量只有 ${mm(minMm)}，小於 ${mm(MIN_SQUEEZE_MM)}，請縮小公差"))
            }
            when {
                maxPct > GlandCalculator.HARD_MAX_COMPRESSION -> add(
                    Issue(Severity.ERROR, "SQUEEZE_OVER", "公差最差時預壓率達 ${pct(maxPct)}，O 型環會永久變形或裝不進去"),
                )
                maxPct > ceiling -> add(
                    Issue(Severity.WARNING, "SQUEEZE_HIGH", "公差最差時預壓率達 ${pct(maxPct)}，高於 ${pct(ceiling)}，安裝力大、易永久變形"),
                )
            }
        }

        return Result(
            nominalMm = nominal,
            nominalPct = nominal / cs,
            minMm = minMm,
            minPct = minPct,
            maxMm = maxMm,
            maxPct = maxPct,
            csTolerance = tol,
            depthTolerance = depthTolerance,
            eccentricity = ecc,
            limits = limits,
            issues = issues,
        )
    }

    private fun pct(v: Double) = String.format(Locale.US, "%.1f%%", v * 100)
    private fun mm(v: Double) = String.format(Locale.US, "%.3f mm", v)
}
