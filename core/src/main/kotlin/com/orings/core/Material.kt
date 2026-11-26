package com.orings.core

/** 接觸介質 */
enum class Medium(val label: String) {
    MINERAL_OIL("礦物油／液壓油"),
    FUEL("汽油／燃油"),
    WATER_STEAM("熱水／蒸汽"),
    OZONE_WEATHER("臭氧／戶外耐候"),
    BRAKE_FLUID("煞車油（乙二醇系 DOT3/4）"),
    PHOSPHATE_ESTER("磷酸酯液壓油（Skydrol）"),
    DILUTE_ACID("稀酸"),
    KETONE("酮類（丙酮、MEK）"),
}

/** 耐受等級 */
enum class Rating(val label: String, val score: Int) {
    GOOD("良好", 2),
    FAIR("尚可", 1),
    POOR("不建議", 0),
}

/**
 * O 型環材質。溫度與耐受性為一般工業經驗值，
 * 實際依配方差異很大，正式選用請以廠商型錄為準。
 */
data class Material(
    val code: String,
    val name: String,
    val tempMinC: Int,
    val tempMaxC: Int,
    /** 適合動態（往復）使用：耐磨、抗撕裂 */
    val dynamicOk: Boolean,
    /** 相對成本 1（便宜）~ 5（昂貴） */
    val relativeCost: Int,
    val resistance: Map<Medium, Rating>,
    val note: String,
) {
    fun rating(medium: Medium): Rating = resistance[medium] ?: Rating.POOR

    val label: String get() = "$code（$name）"
}

object Materials {
    private val G = Rating.GOOD
    private val F = Rating.FAIR
    private val P = Rating.POOR

    private fun r(
        oil: Rating, fuel: Rating, water: Rating, ozone: Rating,
        brake: Rating, phosphate: Rating, acid: Rating, ketone: Rating,
    ) = mapOf(
        Medium.MINERAL_OIL to oil,
        Medium.FUEL to fuel,
        Medium.WATER_STEAM to water,
        Medium.OZONE_WEATHER to ozone,
        Medium.BRAKE_FLUID to brake,
        Medium.PHOSPHATE_ESTER to phosphate,
        Medium.DILUTE_ACID to acid,
        Medium.KETONE to ketone,
    )

    val all: List<Material> = listOf(
        Material(
            "NBR", "丁腈橡膠", -30, 100, dynamicOk = true, relativeCost = 1,
            resistance = r(G, G, F, P, P, P, F, P),
            note = "最常用、便宜；耐油佳，不耐臭氧與日照",
        ),
        Material(
            "HNBR", "氫化丁腈橡膠", -30, 150, dynamicOk = true, relativeCost = 3,
            resistance = r(G, G, G, F, P, P, F, P),
            note = "NBR 強化版，耐溫、耐磨、耐熱水較好",
        ),
        Material(
            "FKM", "氟橡膠（Viton®）", -20, 200, dynamicOk = true, relativeCost = 3,
            resistance = r(G, G, F, G, P, P, G, P),
            note = "耐高溫、耐油與燃油；不耐酮類、煞車油與蒸汽",
        ),
        Material(
            "EPDM", "三元乙丙橡膠", -50, 150, dynamicOk = true, relativeCost = 1,
            resistance = r(P, P, G, G, G, G, G, G),
            note = "耐水、蒸汽、煞車油、耐候；遇礦物油會膨脹",
        ),
        Material(
            "VMQ", "矽橡膠", -60, 200, dynamicOk = false, relativeCost = 2,
            resistance = r(F, P, F, G, F, P, F, F),
            note = "溫度範圍最寬、食品級常用；強度與耐磨差，只適合靜態",
        ),
        Material(
            "FVMQ", "氟矽橡膠", -60, 175, dynamicOk = false, relativeCost = 4,
            resistance = r(G, G, F, G, P, P, F, P),
            note = "耐低溫又耐燃油（航太常用）；強度差，只適合靜態",
        ),
        Material(
            "CR", "氯丁橡膠（Neoprene）", -40, 100, dynamicOk = true, relativeCost = 1,
            resistance = r(F, P, F, G, P, P, F, P),
            note = "耐候、耐冷媒；耐油中等",
        ),
        Material(
            "AU", "聚氨酯（PU）", -30, 80, dynamicOk = true, relativeCost = 2,
            resistance = r(G, F, P, G, P, P, P, P),
            note = "耐磨與抗擠出最好，適合高壓液壓；不耐熱水（水解）",
        ),
        Material(
            "FFKM", "全氟橡膠", -15, 300, dynamicOk = true, relativeCost = 5,
            resistance = r(G, G, G, G, G, G, G, G),
            note = "幾乎耐所有化學品與高溫；價格極高",
        ),
    )

    fun find(code: String): Material? = all.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
}

/** 材質推薦 */
object MaterialAdvisor {

    data class Evaluation(
        val material: Material,
        /** 越高越適合；不適用時為 -1 */
        val score: Int,
        /** 不適用或需注意的原因 */
        val problems: List<String>,
        val cautions: List<String>,
    ) {
        val suitable: Boolean get() = problems.isEmpty()
    }

    /**
     * 依溫度範圍、接觸介質與使用條件評估所有材質。
     * 回傳順序：適用的在前（分數高到低），不適用的在後。
     */
    fun evaluate(
        tempMinC: Int,
        tempMaxC: Int,
        media: Set<Medium>,
        service: Service = Service.STATIC,
    ): List<Evaluation> {
        require(tempMinC <= tempMaxC) { "最低溫度不能高於最高溫度" }
        return Materials.all.map { m ->
            val problems = mutableListOf<String>()
            val cautions = mutableListOf<String>()
            if (tempMinC < m.tempMinC) problems += "低溫只到 ${m.tempMinC}°C"
            if (tempMaxC > m.tempMaxC) problems += "高溫只到 ${m.tempMaxC}°C"
            if (service == Service.DYNAMIC && !m.dynamicOk) problems += "不適合動態使用"
            media.forEach { md ->
                when (m.rating(md)) {
                    Rating.POOR -> problems += "不耐${md.label}"
                    Rating.FAIR -> cautions += "${md.label}僅尚可"
                    Rating.GOOD -> Unit
                }
            }
            // 接近溫度上限（剩不到 10°C）也提醒
            if (tempMaxC <= m.tempMaxC && m.tempMaxC - tempMaxC < 10) cautions += "接近溫度上限"

            val score = if (problems.isNotEmpty()) {
                -1
            } else {
                // 耐受性為主；其次溫度餘裕越多越好；成本越高扣分，讓便宜夠用的材質優先
                media.sumOf { m.rating(it).score } * 10 +
                    minOf(m.tempMaxC - tempMaxC, 50) / 10 -
                    m.relativeCost * 2
            }
            Evaluation(m, score, problems, cautions)
        }.sortedWith(compareByDescending<Evaluation> { it.suitable }.thenByDescending { it.score })
    }
}
