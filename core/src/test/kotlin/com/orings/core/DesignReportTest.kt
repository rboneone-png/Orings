package com.orings.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignReportTest {

    private val base = DesignRecord(
        name = "測試活塞", savedAt = "2026-11-26 16:30",
        sealType = SealType.PISTON, service = Service.STATIC, unit = LengthUnit.MM,
        ringId = 20.0, ringCs = 3.53, sizeLabel = null,
        mateDiameter = 26.0, grooveDiameter = 20.5, grooveWidth = 4.8, faceDepth = 0.0,
        materialCode = null,
        pressureMpa = null, pressureUnit = PressureUnit.MPA, radialGap = 0.1, hardness = null, backupRings = null,
        tMinC = null, tMaxC = null, hardware = HardwareMaterial.STEEL, strictSurface = false,
    )

    private fun titles(r: DesignReport.Report) = r.sections.map { it.title }

    @Test
    fun minimalReport() {
        val r = DesignReport.build(base)
        assertEquals(DesignReport.Verdict.OK, r.verdict)
        assertEquals(
            listOf("基本資料", "O 型環", "溝槽", "計算結果（組裝溫度）", "表面粗糙度建議（靜態）", "注意事項"),
            titles(r),
        )
        val text = DesignReport.toText(r)
        assertTrue(text, text.contains("壓縮率：21.1%（建議 15.0%–30.0%）"))
        assertTrue(text.contains("內徑 ID：20 mm"))
        assertTrue(text.contains("無問題"))
        assertTrue(text.contains("材質：未指定（以 NBR 計算膨脹）"))
    }

    @Test
    fun fullReportHasPressureAndThermal() {
        val r = DesignReport.build(
            base.copy(
                pressureMpa = 20.0, pressureUnit = PressureUnit.BAR,
                tMinC = -20.0, tMaxC = 150.0, materialCode = "FKM", sizeLabel = "AS568 -211",
            ),
        )
        assertTrue("壓力與硬度" in titles(r))
        assertTrue("熱膨脹修正" in titles(r))
        val text = DesignReport.toText(r)
        assertTrue(text, text.contains("工作壓力：200.00 bar（20.00 MPa）"))
        assertTrue(text.contains("採用硬度：Shore 90A（自動）"))
        assertTrue(text.contains("擋圈：單側（自動）"))
        assertTrue(text.contains("最高溫 150°C："))
        assertTrue(text.contains("規格：AS568 -211"))
        // 4.8 寬扣單側擋圈 1.4 後放不下：不合格
        assertEquals(DesignReport.Verdict.ERROR, r.verdict)
    }

    @Test
    fun thermalWarningMakesVerdictWarning() {
        val r = DesignReport.build(base.copy(tMinC = -20.0, tMaxC = 150.0)) // NBR 上限 100°C
        assertEquals(DesignReport.Verdict.WARNING, r.verdict)
        assertTrue(DesignReport.toText(r).contains("! 最高溫 150°C 高於 NBR 上限 100°C"))
    }

    @Test
    fun inchAndFaceSeal() {
        val r = DesignReport.build(
            base.copy(
                unit = LengthUnit.INCH, sealType = SealType.FACE, grooveDiameter = 20.0, faceDepth = 2.7,
                service = Service.DYNAMIC, strictSurface = true,
            ),
        )
        val text = DesignReport.toText(r)
        assertTrue(text, text.contains("溝槽內徑："))
        assertTrue(text.contains(" in"))
        assertTrue(text.contains("µin"))
        assertTrue(titles(r).any { it.startsWith("表面粗糙度建議（動態（往復），壓力脈動") })
        assertFalse(text.contains("缸筒內徑"))
    }

    @Test
    fun recordOperatingMatchesManualSetting() {
        val rec = base.copy(pressureMpa = 7.0, hardness = Hardness.A70, backupRings = 0)
        val op = rec.operating()!!
        assertEquals(Hardness.A70, op.hardness)
        assertEquals(0, op.backupRings)
        assertEquals(null, base.operating())
        assertEquals(null, base.thermal())
    }
}
