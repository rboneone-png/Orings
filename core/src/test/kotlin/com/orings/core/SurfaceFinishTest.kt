package com.orings.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceFinishTest {

    private fun of(list: List<Roughness>, s: Surface) = list.first { it.surface == s }

    @Test
    fun everyCaseCoversAllSurfaces() {
        for (service in Service.entries) for (strict in listOf(false, true)) {
            val list = SurfaceFinish.recommend(service, strict)
            assertEquals(Surface.entries, list.map { it.surface })
            list.forEach { r ->
                assertTrue("$service $strict $r", r.raMax > 0 && r.rzMax > r.raMax)
                r.raMin?.let { assertTrue(it < r.raMax) }
            }
        }
    }

    @Test
    fun dynamicContactHasRangeAndIsFinest() {
        val dyn = of(SurfaceFinish.recommend(Service.DYNAMIC), Surface.CONTACT)
        assertNotNull(dyn.raMin)
        assertEquals(0.4, dyn.raMax, 1e-9)
        val stat = of(SurfaceFinish.recommend(Service.STATIC), Surface.CONTACT)
        assertNull(stat.raMin)
        assertTrue(dyn.raMax < stat.raMax)
    }

    @Test
    fun strictIsNeverCoarser() {
        for (service in Service.entries) {
            val normal = SurfaceFinish.recommend(service, false)
            val strict = SurfaceFinish.recommend(service, true)
            normal.zip(strict).forEach { (n, s) ->
                assertTrue("$service ${n.surface}", s.raMax <= n.raMax && s.rzMax <= n.rzMax)
            }
        }
        assertEquals(0.8, of(SurfaceFinish.recommend(Service.STATIC, true), Surface.CONTACT).raMax, 1e-9)
    }

    @Test
    fun groovesAreCoarserThanDynamicContact() {
        val list = SurfaceFinish.recommend(Service.DYNAMIC)
        val contact = of(list, Surface.CONTACT).raMax
        assertTrue(of(list, Surface.GROOVE_BOTTOM).raMax > contact)
        assertTrue(of(list, Surface.GROOVE_FLANK).raMax >= of(list, Surface.GROOVE_BOTTOM).raMax)
    }
}
