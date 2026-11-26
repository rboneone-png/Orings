package com.orings.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SizeCatalogTest {

    private fun as568(dash: String) = As568.find(dash)!!
    private fun jis(code: String) = JisB2401.find(code)!!

    @Test
    fun as568_countAndUniqueCodes() {
        // 001–003, 004–050, 102–178, 201–284, 309–395, 425–475, 20 個 9xx
        assertEquals(3 + 47 + 77 + 84 + 87 + 51 + 20, As568.sizes.size)
        assertEquals(As568.sizes.size, As568.sizes.map { it.code }.toSet().size)
    }

    @Test
    fun as568_spotValues() {
        // (代號, ID inch, CS inch)
        val expected = listOf(
            Triple("-001", 0.029, 0.040),
            Triple("-010", 0.239, 0.070),
            Triple("-029", 1.489, 0.070),
            Triple("-050", 5.239, 0.070),
            Triple("-110", 0.362, 0.103),
            Triple("-151", 2.987, 0.103),
            Triple("-178", 9.737, 0.103),
            Triple("-210", 0.734, 0.139),
            Triple("-258", 5.984, 0.139),
            Triple("-274", 12.984, 0.139),
            Triple("-281", 19.984, 0.139),
            Triple("-325", 1.475, 0.210),
            Triple("-349", 4.475, 0.210),
            Triple("-371", 9.975, 0.210),
            Triple("-385", 20.975, 0.210),
            Triple("-395", 30.959, 0.210),
            Triple("-425", 4.475, 0.275),
            Triple("-460", 15.475, 0.275),
            Triple("-475", 25.940, 0.275),
            Triple("-908", 0.644, 0.087),
        )
        for ((dash, id, cs) in expected) {
            val s = as568(dash)
            assertEquals(dash, id, s.idInch!!, 1e-9)
            assertEquals(dash, cs, s.csInch!!, 1e-9)
            assertEquals(dash, id * 25.4, s.innerDiameter, 1e-9)
        }
    }

    @Test
    fun as568_idIncreasesWithinEachSeries() {
        As568.sizes.groupBy { it.code.substring(1, 2) }.forEach { (series, list) ->
            list.zipWithNext().forEach { (a, b) ->
                assertTrue("$series: ${a.code} ${a.idInch} → ${b.code} ${b.idInch}", b.idInch!! > a.idInch!!)
            }
        }
    }

    @Test
    fun as568_findAcceptsVariousFormats() {
        assertEquals("-010", As568.find("10")?.code)
        assertEquals("-010", As568.find("-010")?.code)
        assertEquals("-210", As568.find(" 210 ")?.code)
        assertNull(As568.find("-999"))
        assertNull(As568.find("abc"))
    }

    @Test
    fun jis_spotValues() {
        val expected = mapOf(
            "P3" to (2.8 to 1.9),
            "P10" to (9.8 to 1.9),
            "P10A" to (9.8 to 2.4),
            "P12.5" to (12.3 to 2.4),
            "P22" to (21.8 to 2.4),
            "P22A" to (21.7 to 3.5),
            "P50" to (49.7 to 3.5),
            "P48A" to (47.6 to 5.7),
            "P150" to (149.6 to 5.7),
            "P150A" to (149.5 to 8.4),
            "P400" to (399.5 to 8.4),
            "G25" to (24.4 to 3.1),
            "G145" to (144.4 to 3.1),
            "G150" to (149.3 to 5.7),
            "G300" to (299.3 to 5.7),
        )
        for ((code, v) in expected) {
            val s = jis(code)
            assertEquals(code, v.first, s.innerDiameter, 1e-9)
            assertEquals(code, v.second, s.crossSection, 1e-9)
        }
        assertEquals("P22", JisB2401.find("p22")?.code)
        assertNull(JisB2401.find("P23"))
    }

    @Test
    fun jis_countsAndOrdering() {
        assertEquals(8 + 12 + 28 + 33 + 41, JisB2401.pSeries.size)
        assertEquals(25 + 31, JisB2401.gSeries.size)
        for (list in listOf(JisB2401.pSeries, JisB2401.gSeries)) {
            assertEquals(list.size, list.map { it.code }.toSet().size)
            list.groupBy { it.crossSection }.values.forEach { g ->
                g.zipWithNext().forEach { (a, b) -> assertTrue("${a.code}→${b.code}", b.innerDiameter > a.innerDiameter) }
            }
        }
    }

    @Test
    fun catalog_findAndSearch() {
        assertEquals(Standard.JIS_G, SizeCatalog.find("G50")?.standard)
        assertEquals(Standard.AS568, SizeCatalog.find("210")?.standard)

        val byCode = SizeCatalog.search("P2", setOf(Standard.JIS_P))
        assertTrue(byCode.all { it.standard == Standard.JIS_P && it.code.contains("P2") })
        assertNotNull(byCode.firstOrNull { it.code == "P22" })

        val byId = SizeCatalog.search("21.8", idTolerance = 0.1)
        assertTrue(byId.any { it.code == "P22" })
        assertTrue(byId.all { abs(it.innerDiameter - 21.8) <= 0.1 || it.code.contains("21.8") })
    }

    @Test
    fun suitableFor_returnsValidCandidates() {
        for (sealType in SealType.entries) {
            for (service in Service.entries) {
                val list = SizeCatalog.suitableFor(sealType, service, 40.0)
                assertTrue("$sealType $service 應有候選", list.isNotEmpty())
                list.forEach { c ->
                    assertTrue("${c.size.displayName} ${c.result.issues}", c.result.issues.isEmpty())
                }
                // 每種標準＋線徑只出現一次
                assertEquals(list.size, list.map { it.size.standard to it.size.crossSection }.toSet().size)
            }
        }
    }

    @Test
    fun suitableFor_excludesPortSealsAndStaticOnlySizes() {
        val static = SizeCatalog.suitableFor(SealType.PISTON, Service.STATIC, 40.0)
        assertTrue(static.none { it.size.isPortSeal })
        assertTrue(static.any { it.size.standard == Standard.JIS_G })
        val dynamic = SizeCatalog.suitableFor(SealType.PISTON, Service.DYNAMIC, 40.0)
        assertTrue(dynamic.none { it.size.standard == Standard.JIS_G })
    }

    @Test
    fun suitableFor_pistonBore40_prefersMatchingJisSize() {
        // 缸筒 Ø40 的活塞密封，JIS 慣例用 P 系列 CS 3.5 → 預期出現 P34~P36 附近
        val c = SizeCatalog.suitableFor(SealType.PISTON, Service.STATIC, 40.0, setOf(Standard.JIS_P))
            .first { it.size.crossSection == 3.5 }
        assertTrue(c.size.code, c.size.innerDiameter in 32.0..34.0)
        assertTrue(c.result.stretch in 0.0..0.05)
    }
}
