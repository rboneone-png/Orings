package com.orings.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * O 型環設計：已知溝槽，反推合適的 O 型環。
 *
 * 1. 理想尺寸：拉伸率取目標值（活塞、端面取上限一半；軸桿取 0），
 *    壓縮率取建議範圍中間值，反推內徑與線徑。
 * 2. 標準尺寸：逐一用溝槽檢查所有標準 O 型環，依「問題數 → 偏離目標程度」排序。
 * 3. 硬度：若提供壓力與間隙，推薦硬度並判斷是否需要擋圈。
 */
object RingDesigner {

    data class Match(val size: StandardSize, val result: GlandResult, val score: Double)

    data class Design(
        /** 理想內徑（mm） */
        val idealInnerDiameter: Double,
        /** 理想線徑（mm） */
        val idealCrossSection: Double,
        /** 理想尺寸套入溝槽的計算結果 */
        val idealResult: GlandResult,
        val targetCompression: Double,
        val targetStretch: Double,
        /** 硬度推薦；未提供壓力時為 null */
        val hardness: Extrusion.Recommendation?,
        /** 依合適程度排序的標準尺寸（只含沒有錯誤的） */
        val matches: List<Match>,
    )

    /**
     * @param pressureMpa 工作壓力；null 表示不考慮壓力
     * @param radialGap 擠出間隙（mm）
     * @param hardness 指定硬度；null 則採用推薦硬度
     * @param backupRings 擋圈數量；null 則依推薦（需要時用 1 個）
     */
    fun design(
        gland: Gland,
        service: Service = Service.STATIC,
        pressureMpa: Double? = null,
        radialGap: Double = 0.1,
        hardness: Hardness? = null,
        backupRings: Int? = null,
        standards: Set<Standard> = Standard.entries.toSet(),
        limit: Int = 10,
    ): Design {
        val limits = DesignLimits.defaultFor(gland.sealType, service)
        val targetCompression = (limits.minCompression + limits.maxCompression) / 2
        val targetStretch = if (gland.sealType == SealType.ROD) 0.0 else limits.maxStretch / 2

        val recommendation = pressureMpa?.let { Extrusion.recommend(it, radialGap) }
        val operating = pressureMpa?.let {
            Operating(
                pressureMpa = it,
                radialGap = radialGap,
                hardness = hardness ?: recommendation?.hardness ?: Hardness.A90,
                backupRings = backupRings ?: if (recommendation?.needsBackup == true) 1 else 0,
            )
        }

        // 套入直徑：活塞與端面是溝槽直徑，軸桿是軸徑
        val seat = if (gland.sealType == SealType.ROD) gland.mateDiameter else gland.grooveDiameter
        val idealId = seat / (1 + targetStretch)
        val effectiveCs = gland.depth / (1 - targetCompression)
        val idealCs = effectiveCs * sqrt(1 + targetStretch)
        val idealResult = GlandCalculator.calculate(ORing(idealId, idealCs), gland, service, limits, operating)

        val matches = SizeCatalog.all.asSequence()
            .filter { it.standard in standards && !it.isPortSeal && it.suitableForService(service) }
            .map { size ->
                val r = GlandCalculator.calculate(size.toORing(), gland, service, limits, operating)
                val score = r.issues.count { it.severity == Severity.WARNING } * 1.0 +
                    abs(r.compression - targetCompression) * 10 +
                    abs(r.stretch - targetStretch) * 10
                Match(size, r, score)
            }
            .filter { it.result.isOk }
            .sortedBy { it.score }
            .take(limit)
            .toList()

        return Design(idealId, idealCs, idealResult, targetCompression, targetStretch, recommendation, matches)
    }
}
