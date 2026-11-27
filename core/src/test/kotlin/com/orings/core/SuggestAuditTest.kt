package com.orings.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全面檢查「推薦溝槽」：所有標準尺寸 × 密封型式 × 使用條件 × 壓力 × 溫度，
 * 推薦結果在組裝、最低溫、最高溫都不能有錯誤；一般條件下也不能有警告。
 */
class SuggestAuditTest {

    private val sizes = SizeCatalog.all.filter { !it.isPortSeal }

    private fun autoOperating(p: Double): Operating {
        val r = Extrusion.recommend(p, 0.1)
        return Operating(p, 0.1, r.hardness!!, if (r.needsBackup) 1 else 0)
    }

    private fun check(thermal: ThermalInput?, allowWarnings: Boolean) {
        val problems = mutableListOf<String>()
        for (seal in SealType.entries) for (service in Service.entries)
            for (op in listOf(null, autoOperating(5.0), autoOperating(20.0))) for (size in sizes) {
                if (!size.suitableForService(service)) continue
                val ring = size.toORing()
                val g = GlandCalculator.suggest(ring, seal, service, null, operating = op, thermal = thermal)
                val results = buildList {
                    add("組裝" to GlandCalculator.calculate(ring, g, service, operating = op))
                    thermal?.let { Thermal.analyze(ring, g, it, service, op).points.drop(1).forEach { p -> add(p.label to p.result) } }
                }
                results.forEach { (where, r) ->
                    r.issues.filter { allowWarnings.not() || it.severity == Severity.ERROR }.forEach {
                        problems += "$seal $service ${op?.pressureMpa} ${size.displayName} $where ${it.code}"
                    }
                }
            }
        assertTrue("${problems.size} 個問題，例如：${problems.take(5)}", problems.isEmpty())
    }

    @Test
    fun noIssuesWithoutTemperature() = check(null, allowWarnings = false)

    @Test
    fun noIssuesForTypicalNbrOnSteel() =
        check(ThermalInput.of(-20.0, 100.0, Materials.find("NBR"), HardwareMaterial.STEEL), allowWarnings = false)

    @Test
    fun noErrorsEvenForExtremeTemperatures() =
        check(ThermalInput.of(-60.0, 200.0, Materials.find("VMQ"), HardwareMaterial.ALUMINUM), allowWarnings = true)

    @Test
    fun autoMateDiameterHitsTargetStretch() {
        val ring = ORing(20.0, 3.53)
        for (service in Service.entries) {
            val limits = DesignLimits.defaultFor(SealType.PISTON, service)
            val g = GlandCalculator.suggest(ring, SealType.PISTON, service)
            val r = GlandCalculator.calculate(ring, g, service)
            assertTrue("$service ${r.stretch}", kotlin.math.abs(r.stretch - limits.maxStretch / 2) < 0.002)
            // 缸筒內徑一併推薦
            assertTrue(g.mateDiameter > g.grooveDiameter)
        }
    }
}
