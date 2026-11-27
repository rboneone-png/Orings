package com.orings.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class GlandCalculatorTest {

    private val eps = 1e-4
    private fun codes(r: GlandResult) = r.issues.map { it.code }.toSet()

    @Test
    fun pistonStatic_withinLimits() {
        val ring = ORing(20.0, 3.53)
        val gland = Gland(SealType.PISTON, grooveWidth = 4.8, grooveDiameter = 20.5, mateDiameter = 26.0)
        val r = GlandCalculator.calculate(ring, gland)

        val stretch = 0.5 / 20.0
        val cs = 3.53 / sqrt(1 + stretch)
        assertEquals(2.75, r.depth, eps)
        assertEquals(stretch, r.stretch, eps)
        assertEquals(cs, r.effectiveCrossSection, eps)
        assertEquals((cs - 2.75) / cs, r.compression, eps)
        assertEquals(PI * cs * cs / 4 / (2.75 * 4.8), r.fill, eps)
        assertTrue(r.issues.toString(), r.issues.isEmpty())
        assertTrue(r.isOk)
    }

    @Test
    fun rodSeal_usesMeanDiameter() {
        // 軸 20、溝槽底徑 24.2 → 溝槽中心徑 22.1；O 型環 20 × 2.62 → 中心徑 22.62
        val ring = ORing(20.0, 2.62)
        val gland = Gland(SealType.ROD, grooveWidth = 3.6, grooveDiameter = 24.2, mateDiameter = 20.0)
        val r = GlandCalculator.calculate(ring, gland)
        assertEquals((22.1 - 22.62) / 22.62, r.stretch, eps)
        // 周向擠壓時不把線徑算細
        assertEquals(2.62, r.effectiveCrossSection, eps)
        assertEquals(2.1, r.depth, eps)
        assertEquals((2.62 - 2.1) / 2.62, r.compression, eps)
        assertTrue(r.issues.toString(), r.isOk)
    }

    @Test
    fun rodSeal_suggestIsContinuousAndSane() {
        // 使用者回報：ID 22、CS 1.778、軸 22.8
        val ring = ORing(22.0, 1.778)
        for (service in Service.entries) {
            val g = GlandCalculator.suggest(ring, SealType.ROD, service, mateDiameter = 22.8)
            val r = GlandCalculator.calculate(ring, g, service)
            val limits = DesignLimits.defaultFor(SealType.ROD, service)
            assertTrue("$service $g ${r.issues}", r.issues.isEmpty())
            assertEquals((limits.minCompression + limits.maxCompression) / 2, r.compression, 0.01)
            // 拉伸以中心徑計算，約 +1.6%～+2.5%，不會被高估成 3.6%
            assertTrue("$service stretch ${r.stretch}", r.stretch in 0.0..0.03)
        }
        // 中心徑模型在 O 型環外徑 ≈ 溝槽底徑附近是連續的
        val a = GlandCalculator.calculate(ring, Gland(SealType.ROD, 2.4, 25.55, 22.8))
        val b = GlandCalculator.calculate(ring, Gland(SealType.ROD, 2.4, 25.56, 22.8))
        assertEquals(a.compression, b.compression, 0.01)
    }

    @Test
    fun noSqueeze_isError() {
        val ring = ORing(20.0, 2.0)
        val gland = Gland(SealType.FACE, grooveWidth = 3.0, grooveDiameter = 20.0, faceDepth = 2.2)
        val r = GlandCalculator.calculate(ring, gland)
        assertTrue("NO_SQUEEZE" in codes(r))
        assertFalse(r.isOk)
    }

    @Test
    fun overSqueezeAndOverfill_areErrors() {
        val ring = ORing(20.0, 3.0)
        val gland = Gland(SealType.FACE, grooveWidth = 3.1, grooveDiameter = 20.0, faceDepth = 1.6)
        val r = GlandCalculator.calculate(ring, gland)
        assertTrue("OVER_SQUEEZE" in codes(r))
        assertTrue("OVERFILL" in codes(r))
        assertFalse(r.isOk)
    }

    @Test
    fun dynamicService_usesTighterLimits() {
        val ring = ORing(20.0, 3.53)
        val gland = Gland(SealType.PISTON, grooveWidth = 4.8, grooveDiameter = 20.5, mateDiameter = 26.0)
        // 靜態 OK 的設計（壓縮 ~21%），在動態下超過 20% 上限
        val r = GlandCalculator.calculate(ring, gland, Service.DYNAMIC)
        assertTrue("HIGH_SQUEEZE" in codes(r))
        assertTrue(r.isOk) // 只是警告
    }

    @Test
    fun highStretch_warnsThenErrors() {
        val ring = ORing(20.0, 2.62)
        val warn = Gland(SealType.PISTON, grooveWidth = 3.6, grooveDiameter = 21.2, mateDiameter = 25.4)
        assertTrue("HIGH_STRETCH" in codes(GlandCalculator.calculate(ring, warn)))

        val err = Gland(SealType.PISTON, grooveWidth = 3.6, grooveDiameter = 22.0, mateDiameter = 26.2)
        assertTrue("OVER_STRETCH" in codes(GlandCalculator.calculate(ring, err)))
    }

    @Test
    fun looseRing_warnsThenErrors() {
        val g = Gland(SealType.PISTON, 4.8, 20.5, 26.0)
        // 內徑大 1%：只警告
        assertTrue("LOOSE_RING" in codes(GlandCalculator.calculate(ORing(20.7, 3.53), g)))
        // 內徑大 19%：錯誤
        val r = GlandCalculator.calculate(ORing(24.4, 3.1), g)
        assertTrue("VERY_LOOSE_RING" in codes(r))
        assertFalse(r.isOk)
        // 軸桿：內徑比軸徑大 10%
        val rod = Gland(SealType.ROD, 3.6, 24.2, 20.0)
        assertTrue("ROD_OVER_COMPRESSED" in codes(GlandCalculator.calculate(ORing(22.0, 2.62), rod)))
    }

    @Test
    fun narrowGroove_isError() {
        val ring = ORing(20.0, 3.53)
        val gland = Gland(SealType.FACE, grooveWidth = 3.0, grooveDiameter = 20.0, faceDepth = 2.7)
        assertTrue("NARROW_GROOVE" in codes(GlandCalculator.calculate(ring, gland)))
    }

    @Test
    fun suggestedGlands_passOwnCheck() {
        val ring = ORing(20.0, 3.53)
        for (service in Service.entries) {
            val piston = GlandCalculator.suggest(ring, SealType.PISTON, service, mateDiameter = 26.0)
            // 軸桿：軸徑略大於 O 型環內徑，外徑才不會被溝槽擠壓過頭
            val rod = GlandCalculator.suggest(ring, SealType.ROD, service, mateDiameter = 21.0)
            val face = GlandCalculator.suggest(ring, SealType.FACE, service)
            for (g in listOf(piston, rod, face)) {
                val r = GlandCalculator.calculate(ring, g, service)
                assertTrue("$service $g -> ${r.issues}", r.issues.isEmpty())
                assertEquals(GlandCalculator.TARGET_FILL, r.fill, 0.03)
            }
        }
    }

    @Test
    fun as568Lookup() {
        val ring = As568.find("210")!!.toORing()
        assertEquals(18.64, ring.innerDiameter, 0.01)
        assertEquals(3.53, ring.crossSection, 0.01)
        assertNull(As568.find("-999"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidGland_rejected() {
        Gland(SealType.PISTON, grooveWidth = 3.0, grooveDiameter = 30.0, mateDiameter = 26.0)
    }
}
