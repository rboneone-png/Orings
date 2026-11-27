package com.orings.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardnessAndDesignTest {

    private fun codes(r: GlandResult) = r.issues.map { it.code }.toSet()

    // ---------- 擠出極限 ----------

    @Test
    fun extrusion_tablePointsAndInterpolation() {
        assertEquals(6.0, Extrusion.maxPressure(Hardness.A70, 0.10), 1e-9)
        assertEquals(10.0, Extrusion.maxPressure(Hardness.A80, 0.10), 1e-9)
        assertEquals(16.0, Extrusion.maxPressure(Hardness.A90, 0.10), 1e-9)
        // 0.10 與 0.15 中間
        assertEquals(5.0, Extrusion.maxPressure(Hardness.A70, 0.125), 1e-9)
        // 間隙小於表格下限取下限值
        assertEquals(25.0, Extrusion.maxPressure(Hardness.A90, 0.01), 1e-9)
        // 間隙大於表格上限依反比遞減
        assertEquals(1.5 * 0.25 / 0.5, Extrusion.maxPressure(Hardness.A70, 0.5), 1e-9)
        // 有擋圈
        assertEquals(Extrusion.BACKUP_LIMIT_MPA, Extrusion.maxPressure(Hardness.A70, 0.3, backupRings = 1), 1e-9)
    }

    @Test
    fun extrusion_harderAndSmallerGapHoldsMore() {
        for (gap in listOf(0.03, 0.08, 0.12, 0.2, 0.4)) {
            val p = Hardness.entries.map { Extrusion.maxPressure(it, gap) }
            assertTrue("gap $gap: $p", p[0] < p[1] && p[1] < p[2])
        }
        for (h in Hardness.entries) {
            assertTrue(Extrusion.maxPressure(h, 0.08) > Extrusion.maxPressure(h, 0.18))
        }
    }

    @Test
    fun recommend_picksSoftestThatHolds() {
        assertEquals(Hardness.A70, Extrusion.recommend(0.0, 0.1).hardness)
        assertEquals(Hardness.A70, Extrusion.recommend(4.0, 0.1).hardness) // 4 ≤ 6 × 0.8
        assertEquals(Hardness.A80, Extrusion.recommend(7.0, 0.1).hardness) // 7 > 4.8，≤ 8
        assertEquals(Hardness.A90, Extrusion.recommend(12.0, 0.1).hardness)
        // 90A 用到極限也不需擋圈
        val r16 = Extrusion.recommend(16.0, 0.1)
        assertEquals(Hardness.A90, r16.hardness)
        assertFalse(r16.needsBackup)
        // 超過 90A 極限：90A + 擋圈
        val r20 = Extrusion.recommend(20.0, 0.1)
        assertEquals(Hardness.A90, r20.hardness)
        assertTrue(r20.needsBackup)
        // 超過擋圈上限：不適用
        assertNull(Extrusion.recommend(40.0, 0.1).hardness)
    }

    @Test
    fun pressureUnit_conversion() {
        assertEquals(1.0, PressureUnit.BAR.toMpa(10.0), 1e-12)
        assertEquals(6.894757, PressureUnit.PSI.toMpa(1000.0), 1e-9)
        assertEquals(100.0, PressureUnit.BAR.fromMpa(10.0), 1e-9)
    }

    // ---------- 溝槽計算加入壓力 ----------

    private val ring = ORing(20.0, 3.53)
    private val gland = Gland(SealType.PISTON, 4.8, 20.5, 26.0)

    @Test
    fun calculate_extrusionErrorAndWarning() {
        val ok = GlandCalculator.calculate(ring, gland, operating = Operating(3.0, 0.1, Hardness.A70))
        assertTrue(ok.issues.toString(), ok.issues.isEmpty())
        assertEquals(6.0, ok.extrusionLimit!!, 1e-9)

        val near = GlandCalculator.calculate(ring, gland, operating = Operating(5.5, 0.1, Hardness.A70))
        assertTrue("NEAR_EXTRUSION" in codes(near))
        assertTrue(near.isOk)

        val bad = GlandCalculator.calculate(ring, gland, operating = Operating(8.0, 0.1, Hardness.A70))
        assertTrue("EXTRUSION" in codes(bad))
        assertFalse(bad.isOk)
    }

    @Test
    fun calculate_backupRingNarrowsGroove() {
        // 原本 4.8 寬可用；加兩個擋圈（各 1.4）後只剩 2.0 < 線徑
        val r = GlandCalculator.calculate(ring, gland, operating = Operating(20.0, 0.1, Hardness.A90, backupRings = 2))
        assertEquals(4.8 - 2.8, r.effectiveWidth, 1e-9)
        assertTrue("NARROW_GROOVE" in codes(r))
        assertFalse("EXTRUSION" in codes(r))
    }

    @Test
    fun calculate_hardRingWarnings() {
        val low = GlandCalculator.calculate(ring, gland, operating = Operating(1.0, 0.1, Hardness.A90))
        assertTrue("HARD_LOW_PRESSURE" in codes(low))
        // 壓縮率約 21%，未超過 25%
        assertFalse("HARD_HIGH_SQUEEZE" in codes(low))

        val tight = Gland(SealType.PISTON, 5.0, 21.0, 26.0) // 深 2.5 → 壓縮約 27%
        val high = GlandCalculator.calculate(ring, tight, operating = Operating(10.0, 0.1, Hardness.A90))
        assertTrue("HARD_HIGH_SQUEEZE" in codes(high))
    }

    @Test
    fun suggest_addsBackupWidth() {
        // 相同硬度下比較：差別只在擋圈
        val plain = GlandCalculator.suggest(
            ring, SealType.PISTON, mateDiameter = 26.0,
            operating = Operating(20.0, 0.1, Hardness.A90, backupRings = 0),
        )
        val withBackup = GlandCalculator.suggest(
            ring, SealType.PISTON, mateDiameter = 26.0,
            operating = Operating(20.0, 0.1, Hardness.A90, backupRings = 1),
        )
        assertEquals(plain.grooveWidth + 1.4, withBackup.grooveWidth, 0.011)
        val r = GlandCalculator.calculate(ring, withBackup, operating = Operating(20.0, 0.1, Hardness.A90, backupRings = 1))
        assertTrue(r.issues.toString(), r.issues.isEmpty())
    }

    @Test
    fun calculate_withoutOperatingIsUnchanged() {
        val r = GlandCalculator.calculate(ring, gland)
        assertNull(r.extrusionLimit)
        assertEquals(4.8, r.effectiveWidth, 1e-9)
    }

    // ---------- O 型環設計（反推） ----------

    @Test
    fun design_idealRingFitsGroove() {
        for (service in Service.entries) {
            for (g in listOf(
                Gland(SealType.PISTON, 4.8, 20.5, 26.0),
                Gland(SealType.ROD, 3.6, 24.2, 20.0),
                Gland(SealType.FACE, 4.0, 30.0, faceDepth = 2.6),
            )) {
                val d = RingDesigner.design(g, service)
                assertEquals("$service $g", d.targetCompression, d.idealResult.compression, 1e-9)
                assertEquals("$service $g", d.targetStretch, d.idealResult.stretch, 1e-9)
            }
        }
    }

    @Test
    fun design_matchesAreValidAndRanked() {
        val d = RingDesigner.design(Gland(SealType.PISTON, 4.8, 20.5, 26.0))
        assertTrue(d.matches.isNotEmpty())
        assertTrue(d.matches.all { it.result.isOk })
        assertEquals(d.matches.map { it.score }.sorted(), d.matches.map { it.score })
        // 溝槽深 2.75 → 應選線徑 3.5 左右的尺寸
        assertTrue(d.matches.first().size.crossSection in 3.3..3.6)
        // 不會推薦內徑比溝槽大很多的尺寸
        assertTrue(d.matches.all { it.result.stretch >= -GlandCalculator.MAX_LOOSE_PISTON })
    }

    @Test
    fun design_withPressureRecommendsHardnessAndBackup() {
        val g = Gland(SealType.PISTON, 6.5, 20.5, 26.0)
        val d = RingDesigner.design(g, pressureMpa = 20.0, radialGap = 0.1)
        assertEquals(Hardness.A90, d.hardness!!.hardness)
        assertTrue(d.hardness!!.needsBackup)
        assertTrue(d.matches.all { "EXTRUSION" !in codes(it.result) })

        // 指定 70A 且不加擋圈：全部擠出，沒有可用尺寸
        val forced = RingDesigner.design(g, pressureMpa = 20.0, radialGap = 0.1, hardness = Hardness.A70, backupRings = 0)
        assertTrue(forced.matches.isEmpty())
        assertTrue("EXTRUSION" in codes(forced.idealResult))
    }
}
