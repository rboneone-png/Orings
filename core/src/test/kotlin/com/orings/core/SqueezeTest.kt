package com.orings.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SqueezeTest {

    private fun codes(r: Squeeze.Result) = r.issues.map { it.code }.toSet()

    @Test
    fun knownTolerances() {
        assertEquals(0.076, Squeeze.csTolerance(1.778), 1e-9) // AS568 .070
        assertEquals(0.102, Squeeze.csTolerance(3.5306), 1e-9) // AS568 .139
        assertEquals(0.09, Squeeze.csTolerance(2.4), 1e-9) // JIS
        assertEquals(0.15, Squeeze.csTolerance(8.4), 1e-9)
        // 內插：介於 3.53 與 5.33 之間
        val t = Squeeze.csTolerance(4.5)
        assertTrue(t > 0.102 && t < 0.127)
    }

    @Test
    fun nominalMatchesCompression() {
        val ring = ORing(20.0, 3.53)
        val g = Gland(SealType.PISTON, 4.8, 20.5, 26.0)
        val r = Squeeze.analyze(ring, g)
        val calc = GlandCalculator.calculate(ring, g)
        assertEquals(calc.effectiveCrossSection - calc.depth, r.nominalMm, 1e-12)
        assertEquals(calc.compression, r.nominalPct, 1e-12)
        assertTrue(r.minMm < r.nominalMm && r.nominalMm < r.maxMm)
        assertTrue(r.issues.toString(), r.issues.isEmpty())
    }

    @Test
    fun worstCaseFormula() {
        val ring = ORing(30.0, 3.53) // 不拉伸：槽徑 = 30
        val g = Gland(SealType.PISTON, 4.8, 30.0, 35.5) // 深 2.75
        val r = Squeeze.analyze(ring, g, csTolerance = 0.1, depthTolerance = 0.05, eccentricity = 0.1)
        assertEquals((3.53 - 0.1) - (2.75 + 0.05 + 0.1), r.minMm, 1e-9)
        assertEquals((3.53 + 0.1) - (2.75 - 0.05 - 0.1), r.maxMm, 1e-9)
        assertEquals(r.minMm / 3.43, r.minPct, 1e-9)
    }

    @Test
    fun smallCrossSectionIsToleranceSensitive() {
        // CS 1.78，深 1.45（名目約 18.5%），公差＋偏心後最小預壓量很小
        val ring = ORing(20.0, 1.78)
        val g = Gland(SealType.PISTON, 2.4, 20.0, 22.9)
        val r = Squeeze.analyze(ring, g, depthTolerance = 0.05, eccentricity = 0.1)
        assertTrue(r.issues.toString(), "SQUEEZE_LOW" in codes(r) || "SQUEEZE_SMALL" in codes(r))
        // 不計偏心、公差縮小就合格
        val tight = Squeeze.analyze(ring, g, csTolerance = 0.05, depthTolerance = 0.02, eccentricity = 0.0)
        assertTrue(tight.issues.toString(), tight.issues.isEmpty())
    }

    @Test
    fun noSqueezeAndOverSqueeze() {
        val ring = ORing(20.0, 2.0)
        val shallowOk = Gland(SealType.FACE, 3.0, 20.0, faceDepth = 1.9)
        assertTrue("SQUEEZE_NONE" in codes(Squeeze.analyze(ring, shallowOk)))
        val deep = Gland(SealType.FACE, 3.0, 20.0, faceDepth = 1.25)
        val r = Squeeze.analyze(ring, deep, depthTolerance = 0.05)
        assertTrue(r.issues.toString(), "SQUEEZE_OVER" in codes(r))
        assertFalse(r.isOk)
    }

    @Test
    fun faceSealIgnoresEccentricity() {
        val ring = ORing(20.0, 3.53)
        val g = Gland(SealType.FACE, 4.8, 20.0, faceDepth = 2.7)
        assertEquals(0.0, Squeeze.analyze(ring, g, eccentricity = 0.2).eccentricity, 0.0)
    }

    @Test
    fun suggestedGlandsPassWorstCaseForCommonSizes() {
        // 推薦溝槽 + 預設公差（±0.05、不含偏心）：常用線徑（≥ 2.4 mm）最差情況應合格
        for (size in SizeCatalog.all.filter { !it.isPortSeal && it.crossSection >= 2.4 }) {
            for (seal in SealType.entries) {
                val ring = size.toORing()
                val g = GlandCalculator.suggest(ring, seal)
                val r = Squeeze.analyze(ring, g, depthTolerance = 0.05)
                assertTrue("${size.displayName} $seal ${r.issues}", r.isOk)
            }
        }
    }
}
