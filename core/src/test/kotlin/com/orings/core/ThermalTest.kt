package com.orings.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalTest {

    private val ring = ORing(20.0, 3.53)
    private val gland = Gland(SealType.PISTON, 4.8, 20.5, 26.0)
    private val nbrSteel = ThermalInput.of(-20.0, 100.0, Materials.find("NBR"), HardwareMaterial.STEEL)

    @Test
    fun allMaterialsHaveReasonableCte() {
        Materials.all.forEach { assertTrue(it.code, it.cte in 0.5e-4..4e-4) }
        HardwareMaterial.entries.forEach {
            val range = if (it.category == HardwareCategory.METAL) 5e-6..30e-6 else 15e-6..250e-6
            assertTrue(it.name, it.cte in range)
        }
        // 兩類都有，且名稱不重複
        assertTrue(HardwareMaterial.entries.count { it.category == HardwareCategory.METAL } >= 10)
        assertTrue(HardwareMaterial.entries.count { it.category == HardwareCategory.PLASTIC } >= 10)
        assertEquals(HardwareMaterial.entries.size, HardwareMaterial.entries.map { it.label }.toSet().size)
        // 橡膠膨脹係數約為鋼的 10 倍以上
        assertTrue(Materials.all.minOf { it.cte } > 10 * HardwareMaterial.STEEL.cte)
    }

    @Test
    fun assemblyPointEqualsPlainCalculation() {
        val a = Thermal.analyze(ring, gland, nbrSteel)
        val p0 = a.points.first()
        assertEquals(20.0, p0.tempC, 1e-9)
        val plain = GlandCalculator.calculate(ring, gland)
        assertEquals(plain.compression, p0.result.compression, 1e-12)
        assertEquals(plain.fill, p0.result.fill, 1e-12)
    }

    @Test
    fun hotIncreasesFillAndSqueeze_coldDecreases() {
        val (room, cold, hot) = Thermal.analyze(ring, gland, nbrSteel).points.map { it.result }
        assertTrue(hot.fill > room.fill && room.fill > cold.fill)
        assertTrue(hot.compression > room.compression && room.compression > cold.compression)
    }

    @Test
    fun scalingMatchesFormula() {
        val (r, g) = Thermal.at(ring, gland, nbrSteel, 100.0)
        assertEquals(3.53 * (1 + 1.3e-4 * 80), r.crossSection, 1e-12)
        assertEquals(26.0 * (1 + 11.5e-6 * 80), g.mateDiameter, 1e-12)
        val fRoom = GlandCalculator.calculate(ring, gland).fill
        // hotFillFactor 是忽略拉伸變化的近似值；高溫時拉伸變小，實際填充率略高
        val actual = GlandCalculator.calculate(r, g).fill
        assertEquals(fRoom * Thermal.hotFillFactor(nbrSteel), actual, 0.01)
        assertTrue(actual >= fRoom * Thermal.hotFillFactor(nbrSteel))
    }

    @Test
    fun hotOverfillIsCaught() {
        // 組裝時填充率約 85%，加熱到 200°C 後超過上限
        val tight = Gland(SealType.PISTON, 4.15, 20.5, 26.0)
        val roomFill = GlandCalculator.calculate(ring, tight).fill
        assertTrue(roomFill in 0.8..0.85)
        val hot = Thermal.analyze(ring, tight, ThermalInput.of(20.0, 200.0, Materials.find("VMQ"), HardwareMaterial.STEEL))
            .points.last().result
        assertTrue(hot.issues.toString(), hot.issues.any { it.code == "HIGH_FILL" || it.code == "OVERFILL" })
    }

    @Test
    fun coldLossOfSqueezeIsCaught() {
        // 鋁殼 + 矽橡膠在 -60°C：壓縮率降到下限以下
        val shallow = Gland(SealType.PISTON, 4.8, 20.1, 26.0) // 深 2.95，組裝壓縮約 16%
        val a = Thermal.analyze(ring, shallow, ThermalInput.of(-60.0, 20.0, Materials.find("VMQ"), HardwareMaterial.ALUMINUM))
        assertTrue(a.points[0].result.issues.none { it.code == "LOW_SQUEEZE" })
        assertTrue(a.points[1].result.issues.any { it.code == "LOW_SQUEEZE" })
    }

    @Test
    fun materialRangeNotes() {
        val nbr = Materials.find("NBR")!!
        val a = Thermal.analyze(ring, gland, ThermalInput.of(-40.0, 150.0, nbr, HardwareMaterial.STEEL), material = nbr)
        assertEquals(2, a.notes.size)
        val ok = Thermal.analyze(ring, gland, nbrSteel.copy(tMaxC = 90.0), material = nbr)
        assertTrue(ok.notes.isEmpty())
    }

    @Test
    fun suggestWidensGrooveForHighTemperature() {
        val plain = GlandCalculator.suggest(ring, SealType.PISTON, mateDiameter = 26.0)
        val hotInput = ThermalInput.of(20.0, 200.0, Materials.find("FFKM"), HardwareMaterial.STEEL)
        val hot = GlandCalculator.suggest(ring, SealType.PISTON, mateDiameter = 26.0, thermal = hotInput)
        assertTrue(hot.grooveWidth > plain.grooveWidth)
        val atMax = Thermal.analyze(ring, hot, hotInput).points.last().result
        assertTrue("${atMax.fill}", atMax.fill <= Thermal.HOT_FILL_TARGET + 1e-9)
        assertTrue(atMax.fill > Thermal.HOT_FILL_TARGET - 0.01) // 沒有加寬過頭
        // 只有低溫：寬度不需加寬，且低溫時壓縮率仍在建議範圍內
        val coldInput = ThermalInput.of(-40.0, 20.0, Materials.find("NBR"), HardwareMaterial.STEEL)
        val cold = GlandCalculator.suggest(ring, SealType.PISTON, mateDiameter = 26.0, thermal = coldInput)
        assertEquals(plain.grooveWidth, cold.grooveWidth, 0.05)
        val c = Thermal.analyze(ring, cold, coldInput).points[1].result
        assertTrue(c.issues.toString(), c.issues.isEmpty())
    }

    @Test
    fun ringDesign_thermalExcludesAndRanks() {
        val g = Gland(SealType.PISTON, 4.8, 20.5, 26.0)
        val plain = RingDesigner.design(g)
        assertTrue(plain.idealThermal == null && plain.matches.all { it.thermal == null })

        // 矽橡膠 + 鋁殼 -60～200°C：比較嚴苛，有些尺寸會被排除
        val hard = ThermalInput.of(-60.0, 200.0, Materials.find("VMQ"), HardwareMaterial.ALUMINUM)
        val d = RingDesigner.design(g, thermal = hard, material = Materials.find("VMQ"), limit = 50)
        assertTrue(d.idealThermal != null)
        assertTrue(d.matches.all { it.thermal!!.isOk })
        assertEquals(d.matches.map { it.score }.sorted(), d.matches.map { it.score })
        val plainAll = RingDesigner.design(g, limit = 50)
        assertTrue(d.matches.size <= plainAll.matches.size)
        // 每個被保留的尺寸，在無溫度時也一定合格
        val plainCodes = RingDesigner.design(g, limit = 1000).matches.map { it.size.code }.toSet()
        assertTrue(d.matches.all { it.size.code in plainCodes })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invertedRangeRejected() {
        ThermalInput(100.0, 0.0, 1.3e-4, 11.5e-6)
    }

    @Test
    fun analysisIsOkReflectsAllPoints() {
        assertTrue(Thermal.analyze(ring, gland, nbrSteel).isOk)
        val tight = Gland(SealType.PISTON, 4.15, 20.5, 26.0)
        assertFalse(Thermal.analyze(ring, tight, ThermalInput.of(20.0, 200.0, Materials.find("VMQ"), HardwareMaterial.STEEL)).isOk)
    }
}
