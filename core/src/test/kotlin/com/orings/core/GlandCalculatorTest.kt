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
    fun rodSeal_noStretchWhenRingLargerThanRod() {
        val ring = ORing(20.0, 2.62)
        val gland = Gland(SealType.ROD, grooveWidth = 3.6, grooveDiameter = 24.2, mateDiameter = 20.0)
        val r = GlandCalculator.calculate(ring, gland)
        assertEquals(0.0, r.stretch, eps)
        assertEquals(2.62, r.effectiveCrossSection, eps)
        assertEquals(2.1, r.depth, eps)
        assertEquals((2.62 - 2.1) / 2.62, r.compression, eps)
        assertTrue(r.issues.toString(), r.isOk)
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
            val rod = GlandCalculator.suggest(ring, SealType.ROD, service, mateDiameter = 20.0)
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
