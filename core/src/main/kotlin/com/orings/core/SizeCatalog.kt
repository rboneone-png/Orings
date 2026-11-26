package com.orings.core

import kotlin.math.abs

/** 所有標準尺寸的查詢入口 */
object SizeCatalog {

    val all: List<StandardSize> by lazy { As568.sizes + JisB2401.sizes }

    fun byStandard(standard: Standard): List<StandardSize> = all.filter { it.standard == standard }

    /** 依代號找尺寸，例如 "210"、"-210"、"P22"、"g50" */
    fun find(code: String): StandardSize? =
        JisB2401.find(code) ?: As568.find(code)

    /**
     * 搜尋：
     * - 代號部分比對（"21" 會找到 -210、-021、P21…）
     * - 或輸入數字，找內徑在 ±[idTolerance] mm 內的尺寸
     */
    fun search(
        query: String,
        standards: Set<Standard> = Standard.entries.toSet(),
        idTolerance: Double = 0.5,
    ): List<StandardSize> {
        val q = query.trim().uppercase()
        val pool = all.filter { it.standard in standards }
        if (q.isEmpty()) return pool
        val number = q.toDoubleOrNull()
        return pool.filter { s ->
            s.code.uppercase().contains(q) ||
                (number != null && abs(s.innerDiameter - number) <= idTolerance)
        }
    }

    /** 尺寸推薦結果 */
    data class Candidate(val size: StandardSize, val gland: Gland, val result: GlandResult)

    /**
     * 依配合尺寸找適用的標準 O 型環，並附上推薦溝槽。
     *
     * @param mateDiameter PISTON = 缸筒內徑；ROD = 軸桿外徑；FACE = 溝槽內徑
     * @return 每種「標準＋線徑」只留最合適的一個，依拉伸率接近目標值排序
     */
    fun suitableFor(
        sealType: SealType,
        service: Service,
        mateDiameter: Double,
        standards: Set<Standard> = Standard.entries.toSet(),
    ): List<Candidate> {
        require(mateDiameter > 0) { "請輸入配合直徑" }
        val limits = DesignLimits.defaultFor(sealType, service)
        // 理想拉伸率：活塞與端面略拉伸以免鬆脫；軸桿盡量不拉伸
        val targetStretch = when (sealType) {
            SealType.ROD -> 0.0
            else -> limits.maxStretch / 2
        }

        val candidates = all.asSequence()
            .filter { it.standard in standards && !it.isPortSeal && it.suitableForService(service) }
            // 線徑不能比配合直徑還粗太多，先粗篩
            .filter { it.crossSection * 4 < mateDiameter }
            .mapNotNull { size ->
                val ring = size.toORing()
                val gland = runCatching {
                    val g = GlandCalculator.suggest(ring, sealType, service, mateDiameter)
                    if (sealType == SealType.FACE) g.copy(grooveDiameter = mateDiameter) else g
                }.getOrNull() ?: return@mapNotNull null
                val result = GlandCalculator.calculate(ring, gland, service)
                when {
                    result.issues.isNotEmpty() -> null
                    result.stretch < 0 && sealType != SealType.ROD -> null
                    // 軸桿密封：O 型環內徑過大會在溝槽內鬆晃
                    sealType == SealType.ROD && result.stretch < -0.03 -> null
                    else -> Candidate(size, gland, result)
                }
            }
            .toList()

        return candidates
            .groupBy { it.size.standard to it.size.crossSection }
            .values
            .map { group -> group.minBy { abs(it.result.stretch - targetStretch) } }
            .sortedBy { abs(it.result.stretch - targetStretch) }
    }
}
