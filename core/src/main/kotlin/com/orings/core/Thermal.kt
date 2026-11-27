package com.orings.core

/** 溝槽件材質類別 */
enum class HardwareCategory(val label: String) {
    METAL("金屬"),
    PLASTIC("塑膠"),
}

/**
 * 溝槽件（缸筒、活塞、殼體、法蘭）材質與線膨脹係數（1/°C，室溫附近的近似值）。
 * 前六項的名稱沿用舊版，以便讀回舊的存檔。
 * 塑膠的膨脹係數依配方、纖維方向差異很大，玻纖強化品取流動方向的近似值。
 */
enum class HardwareMaterial(val category: HardwareCategory, val label: String, val cte: Double) {
    // ---- 金屬 ----
    STEEL(HardwareCategory.METAL, "碳鋼 S45C（1045）", 11.5e-6),
    ALLOY_STEEL(HardwareCategory.METAL, "合金鋼 SCM440（4140）", 12.3e-6),
    STAINLESS(HardwareCategory.METAL, "不鏽鋼 SUS304", 17.3e-6),
    STAINLESS_316(HardwareCategory.METAL, "不鏽鋼 SUS316", 16.0e-6),
    STAINLESS_420(HardwareCategory.METAL, "不鏽鋼 SUS420J2", 10.3e-6),
    STAINLESS_440C(HardwareCategory.METAL, "不鏽鋼 SUS440C", 10.2e-6),
    ALUMINUM(HardwareCategory.METAL, "鋁合金 6061-T6", 23.6e-6),
    ALUMINUM_7075(HardwareCategory.METAL, "鋁合金 7075-T6", 23.4e-6),
    ALUMINUM_5052(HardwareCategory.METAL, "鋁合金 5052", 23.8e-6),
    ALUMINUM_DIECAST(HardwareCategory.METAL, "鋁壓鑄 ADC12", 21.0e-6),
    BRASS(HardwareCategory.METAL, "黃銅 C3604", 20.5e-6),
    BRONZE(HardwareCategory.METAL, "磷青銅 C5191", 18.2e-6),
    CAST_IRON(HardwareCategory.METAL, "鑄鐵 FC250", 10.5e-6),
    DUCTILE_IRON(HardwareCategory.METAL, "球墨鑄鐵 FCD450", 11.2e-6),
    TITANIUM(HardwareCategory.METAL, "鈦合金 Ti-6Al-4V", 8.6e-6),
    INCONEL(HardwareCategory.METAL, "鎳基合金 Inconel 718", 13.0e-6),

    // ---- 塑膠 ----
    POM(HardwareCategory.PLASTIC, "POM 聚甲醛", 110e-6),
    PA66(HardwareCategory.PLASTIC, "PA66 尼龍", 85e-6),
    PA66_GF30(HardwareCategory.PLASTIC, "PA66+GF30 玻纖尼龍", 25e-6),
    PBT_GF30(HardwareCategory.PLASTIC, "PBT+GF30", 25e-6),
    PPS_GF40(HardwareCategory.PLASTIC, "PPS+GF40", 20e-6),
    PEEK(HardwareCategory.PLASTIC, "PEEK", 47e-6),
    PEEK_GF30(HardwareCategory.PLASTIC, "PEEK+GF30", 22e-6),
    PTFE(HardwareCategory.PLASTIC, "PTFE 鐵氟龍", 120e-6),
    UHMWPE(HardwareCategory.PLASTIC, "UHMW-PE 超高分子量聚乙烯", 170e-6),
    PP(HardwareCategory.PLASTIC, "PP 聚丙烯", 150e-6),
    PVC(HardwareCategory.PLASTIC, "PVC 硬質", 70e-6),
    PC(HardwareCategory.PLASTIC, "PC 聚碳酸酯", 68e-6),
    ABS(HardwareCategory.PLASTIC, "ABS", 90e-6),
}

/**
 * 熱膨脹修正的輸入。
 *
 * @param ringCte O 型環線膨脹係數（1/°C）
 * @param hardwareCte 金屬件線膨脹係數（1/°C）；簡化為缸筒、活塞、殼體同一種材質
 * @param assemblyC 組裝（量測尺寸）時的溫度
 */
data class ThermalInput(
    val tMinC: Double,
    val tMaxC: Double,
    val ringCte: Double,
    val hardwareCte: Double,
    val assemblyC: Double = 20.0,
) {
    init {
        require(tMinC <= tMaxC) { "最低溫度不能高於最高溫度" }
        require(ringCte > 0 && hardwareCte > 0) { "膨脹係數必須大於 0" }
    }

    companion object {
        fun of(tMinC: Double, tMaxC: Double, material: Material?, hardware: HardwareMaterial) =
            ThermalInput(tMinC, tMaxC, (material ?: Materials.find("NBR")!!).cte, hardware.cte)
    }
}

/**
 * 熱膨脹修正：把 O 型環與溝槽尺寸各自依膨脹係數換算到指定溫度後重新計算。
 *
 * 橡膠的膨脹係數約為鋼的 10～20 倍：
 * - 高溫：O 型環膨脹比溝槽多 → 壓縮率、填充率上升，可能擠爆溝槽
 * - 低溫：O 型環收縮比溝槽多 → 壓縮率下降，可能洩漏
 *
 * 未考慮玻璃轉化溫度附近的硬化、介質膨潤與壓縮永久變形。
 */
object Thermal {

    /** 高溫時的填充率目標：推薦溝槽時確保高溫填充率不超過此值 */
    const val HOT_FILL_TARGET = 0.80

    data class Point(
        val label: String,
        val tempC: Double,
        val ring: ORing,
        val gland: Gland,
        val result: GlandResult,
    )

    data class Analysis(
        val points: List<Point>,
        /** 溫度超出材質範圍等提醒 */
        val notes: List<String>,
    ) {
        /** 任一溫度有錯誤 */
        val isOk: Boolean get() = points.all { it.result.isOk }
    }

    fun scaleRing(ring: ORing, cte: Double, dT: Double): ORing {
        val f = 1 + cte * dT
        return ORing(ring.innerDiameter * f, ring.crossSection * f)
    }

    fun scaleGland(gland: Gland, cte: Double, dT: Double): Gland {
        val f = 1 + cte * dT
        return gland.copy(
            grooveWidth = gland.grooveWidth * f,
            grooveDiameter = gland.grooveDiameter * f,
            mateDiameter = gland.mateDiameter * f,
            faceDepth = gland.faceDepth * f,
        )
    }

    /** 溫度 [tempC] 時的 O 型環與溝槽尺寸 */
    fun at(ring: ORing, gland: Gland, input: ThermalInput, tempC: Double): Pair<ORing, Gland> {
        val dT = tempC - input.assemblyC
        return scaleRing(ring, input.ringCte, dT) to scaleGland(gland, input.hardwareCte, dT)
    }

    fun analyze(
        ring: ORing,
        gland: Gland,
        input: ThermalInput,
        service: Service = Service.STATIC,
        operating: Operating? = null,
        material: Material? = null,
    ): Analysis {
        val temps = listOf(
            "組裝" to input.assemblyC,
            "最低溫" to input.tMinC,
            "最高溫" to input.tMaxC,
        )
        val points = temps.map { (label, t) ->
            val (r, g) = at(ring, gland, input, t)
            Point(label, t, r, g, GlandCalculator.calculate(r, g, service, operating = operating))
        }
        val notes = buildList {
            if (material != null) {
                if (input.tMinC < material.tempMinC) add("最低溫 ${fmt(input.tMinC)}°C 低於 ${material.code} 下限 ${material.tempMinC}°C，O 型環會變硬失去彈性")
                if (input.tMaxC > material.tempMaxC) add("最高溫 ${fmt(input.tMaxC)}°C 高於 ${material.code} 上限 ${material.tempMaxC}°C，O 型環會老化或分解")
            }
        }
        return Analysis(points, notes)
    }

    /** 高溫時填充率相對組裝時的放大倍數：((1 + αr·ΔT) / (1 + αm·ΔT))² */
    fun hotFillFactor(input: ThermalInput): Double {
        val dT = input.tMaxC - input.assemblyC
        if (dT <= 0) return 1.0
        val k = (1 + input.ringCte * dT) / (1 + input.hardwareCte * dT)
        return k * k
    }

    private fun fmt(v: Double) = "%.1f".format(java.util.Locale.US, v).removeSuffix(".0")
}
