package com.orings.core

/** 金屬件材質與線膨脹係數（1/°C，室溫附近的近似值） */
enum class HardwareMaterial(val label: String, val cte: Double) {
    STEEL("碳鋼", 11.5e-6),
    STAINLESS("不鏽鋼 304", 17.3e-6),
    ALUMINUM("鋁合金", 23.0e-6),
    BRASS("黃銅", 19.0e-6),
    CAST_IRON("鑄鐵", 10.5e-6),
    TITANIUM("鈦合金", 8.6e-6),
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
