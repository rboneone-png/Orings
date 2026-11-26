package com.orings.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitAndMaterialTest {

    // ---------- 單位 ----------

    @Test
    fun lengthUnit_conversion() {
        assertEquals(25.4, LengthUnit.INCH.toMm(1.0), 1e-12)
        assertEquals(1.0, LengthUnit.INCH.fromMm(25.4), 1e-12)
        assertEquals(3.53, LengthUnit.MM.toMm(3.53), 1e-12)
    }

    @Test
    fun lengthUnit_format() {
        assertEquals("1", LengthUnit.INCH.format(25.4))
        assertEquals("0.139", LengthUnit.INCH.format(0.139 * 25.4))
        assertEquals("3.53", LengthUnit.MM.format(3.53))
        assertEquals("20", LengthUnit.MM.format(20.0))
        assertEquals("0.1", LengthUnit.MM.format(0.1004))
    }

    @Test
    fun lengthUnit_parse() {
        assertEquals(25.4, LengthUnit.INCH.parseToMm("1")!!, 1e-12)
        assertEquals(20.5, LengthUnit.MM.parseToMm(" 20,5 ")!!, 1e-12)
        assertNull(LengthUnit.MM.parseToMm("abc"))
    }

    @Test
    fun lengthUnit_convertTextRoundTrip() {
        val inch = LengthUnit.INCH.convertText("18.64", from = LengthUnit.MM)
        assertEquals("0.7339", inch)
        assertEquals("18.641", LengthUnit.MM.convertText(inch, from = LengthUnit.INCH))
        assertEquals("", LengthUnit.INCH.convertText("", from = LengthUnit.MM))
    }

    // ---------- 材質 ----------

    @Test
    fun materials_dataIsComplete() {
        assertEquals(Materials.all.size, Materials.all.map { it.code }.toSet().size)
        Materials.all.forEach { m ->
            assertTrue(m.code, m.tempMinC < m.tempMaxC)
            assertTrue(m.code, m.relativeCost in 1..5)
            assertEquals(m.code, Medium.entries.toSet(), m.resistance.keys)
        }
        assertEquals("FKM", Materials.find("fkm")?.code)
    }

    @Test
    fun advisor_hydraulicOil_prefersNbr() {
        val list = MaterialAdvisor.evaluate(-20, 80, setOf(Medium.MINERAL_OIL), Service.DYNAMIC)
        assertEquals("NBR", list.first().material.code)
        val epdm = list.first { it.material.code == "EPDM" }
        assertFalse(epdm.suitable)
        assertTrue(epdm.problems.any { it.contains("礦物油") })
        // 矽橡膠不適合動態
        assertFalse(list.first { it.material.code == "VMQ" }.suitable)
    }

    @Test
    fun advisor_brakeFluid_prefersEpdm() {
        val list = MaterialAdvisor.evaluate(-40, 120, setOf(Medium.BRAKE_FLUID))
        assertEquals("EPDM", list.first().material.code)
        val ok = list.filter { it.suitable }.map { it.material.code }.toSet()
        assertFalse("NBR" in ok)
        assertFalse("FKM" in ok)
        // FFKM 低溫只到 -15°C
        assertFalse("FFKM" in ok)
    }

    @Test
    fun advisor_highTempFuel_prefersFkm() {
        val list = MaterialAdvisor.evaluate(-10, 180, setOf(Medium.FUEL, Medium.MINERAL_OIL))
        assertEquals("FKM", list.first().material.code)
        assertTrue(list.first { it.material.code == "NBR" }.problems.any { it.contains("高溫") })
    }

    @Test
    fun advisor_lowTemp_excludesFkm() {
        val fkm = MaterialAdvisor.evaluate(-50, 20, emptySet()).first { it.material.code == "FKM" }
        assertFalse(fkm.suitable)
        assertTrue(fkm.problems.any { it.contains("低溫") })
    }

    @Test
    fun advisor_cautionNearTempLimit() {
        val nbr = MaterialAdvisor.evaluate(0, 95, setOf(Medium.MINERAL_OIL)).first { it.material.code == "NBR" }
        assertTrue(nbr.suitable)
        assertTrue(nbr.cautions.contains("接近溫度上限"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun advisor_rejectsInvertedRange() {
        MaterialAdvisor.evaluate(100, 0, emptySet())
    }
}
