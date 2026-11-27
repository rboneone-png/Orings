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
 * 4. 熱膨脹：若提供溫度範圍，標準尺寸在任一溫度出錯即排除，高低溫的警告也計入排序。
 * 5. 預壓量：若提供溝槽深度公差，以各尺寸的線徑公差算最差情況預壓量，出錯即排除，警告計入排序。
 */
object RingDesigner {

    data class Match(
        val size: StandardSize,
        val result: GlandResult,
        val score: Double,
        /** 熱膨脹修正；未提供溫度時為 null */
        val thermal: Thermal.Analysis? = null,
        /** 預壓量（含公差）；未提供公差時為 null */
        val squeeze: Squeeze.Result? = null,
    )

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
        /** 理想尺寸的熱膨脹修正；未提供溫度時為 null */
        val idealThermal: Thermal.Analysis? = null,
        /** 理想尺寸的預壓量（含公差）；未提供公差時為 null */
        val idealSqueeze: Squeeze.Result? = null,
    )

    /**
     * @param pressureMpa 工作壓力；null 表示不考慮壓力
     * @param radialGap 擠出間隙（mm）
     * @param hardness 指定硬度；null 則採用推薦硬度
     * @param backupRings 擋圈數量；null 則依推薦（需要時用 1 個）
     * @param thermal 溫度範圍與膨脹係數；null 則不做熱膨脹修正
     * @param material O 型環材質，用於溫度範圍提醒
     * @param depthTolerance 溝槽深度公差（±mm）；null 則不做預壓量判定
     * @param eccentricity 預壓量計算的偏心量（mm），端面密封不適用
     */
    fun design(
        gland: Gland,
        service: Service = Service.STATIC,
        pressureMpa: Double? = null,
        radialGap: Double = 0.1,
        hardness: Hardness? = null,
        backupRings: Int? = null,
        thermal: ThermalInput? = null,
        material: Material? = null,
        depthTolerance: Double? = null,
        eccentricity: Double = 0.0,
        standards: Set<Standard> = Standard.entries.toSet(),
        limit: Int = 10,
    ): Design {
        val limits = DesignLimits.defaultFor(gland.sealType, service)
        val targetCompression = (limits.minCompression + limits.maxCompression) / 2
        val targetStretch = GlandCalculator.targetStretch(gland.sealType, limits)

        val recommendation = pressureMpa?.let { Extrusion.recommend(it, radialGap) }
        val operating = pressureMpa?.let {
            Operating(
                pressureMpa = it,
                radialGap = radialGap,
                hardness = hardness ?: recommendation?.hardness ?: Hardness.A90,
                backupRings = backupRings ?: if (recommendation?.needsBackup == true) 1 else 0,
            )
        }

        val effectiveCs = gland.depth / (1 - targetCompression)
        val idealCs = effectiveCs * sqrt(1 + targetStretch)
        val idealId = if (gland.sealType == SealType.ROD) {
            // 軸桿：O 型環中心徑 = 溝槽中心徑 ÷ (1 + 目標拉伸)
            (gland.mateDiameter + gland.grooveDiameter) / 2 / (1 + targetStretch) - idealCs
        } else {
            gland.grooveDiameter / (1 + targetStretch)
        }
        val idealRing = ORing(idealId, idealCs)
        val idealResult = GlandCalculator.calculate(idealRing, gland, service, limits, operating)
        val idealThermal = thermal?.let { Thermal.analyze(idealRing, gland, it, service, operating, material) }
        fun squeezeOf(ring: ORing) = depthTolerance?.let {
            Squeeze.analyze(ring, gland, service, depthTolerance = it, eccentricity = eccentricity, limits = limits)
        }
        val idealSqueeze = squeezeOf(idealRing)

        val matches = SizeCatalog.all.asSequence()
            .filter { it.standard in standards && !it.isPortSeal && it.suitableForService(service) }
            .mapNotNull { size ->
                val ring = size.toORing()
                val r = GlandCalculator.calculate(ring, gland, service, limits, operating)
                if (!r.isOk) return@mapNotNull null
                val t = thermal?.let { Thermal.analyze(ring, gland, it, service, operating, material) }
                if (t != null && !t.isOk) return@mapNotNull null
                val sq = squeezeOf(ring)
                if (sq != null && !sq.isOk) return@mapNotNull null
                // 高低溫的警告也計入（組裝溫度的已算在 r 裡）
                val thermalWarnings = t?.points?.drop(1)?.sumOf { p -> p.result.issues.count { it.severity == Severity.WARNING } } ?: 0
                val squeezeWarnings = sq?.issues?.size ?: 0
                val score = (r.issues.count { it.severity == Severity.WARNING } + thermalWarnings + squeezeWarnings) * 1.0 +
                    abs(r.compression - targetCompression) * 10 +
                    abs(r.stretch - targetStretch) * 10
                Match(size, r, score, t, sq)
            }
            .sortedBy { it.score }
            .take(limit)
            .toList()

        return Design(idealId, idealCs, idealResult, targetCompression, targetStretch, recommendation, matches, idealThermal, idealSqueeze)
    }
}
