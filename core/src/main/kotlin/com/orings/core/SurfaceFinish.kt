package com.orings.core

/** 需要控制粗糙度的表面 */
enum class Surface(val label: String) {
    CONTACT("配合面（O 型環接觸／滑動的面）"),
    GROOVE_BOTTOM("溝槽底面"),
    GROOVE_FLANK("溝槽側面"),
}

/**
 * 表面粗糙度建議值，單位 µm。
 *
 * @param raMin Ra 下限；只有動態滑動面有（太光滑留不住潤滑油膜）
 */
data class Roughness(
    val surface: Surface,
    val raMin: Double?,
    val raMax: Double,
    val rzMax: Double,
    val note: String,
)

/**
 * 表面粗糙度建議（一般密封件廠商設計指引的經驗值，正式設計請以廠商型錄為準）。
 *
 * - 動態：滑動面要光滑但不能太光滑（Ra 0.1–0.4 µm），並要求材料支承率
 * - 靜態：接觸面 Ra ≤ 1.6 µm；壓力脈動或密封氣體、真空時要更細（Ra ≤ 0.8 µm）
 */
object SurfaceFinish {
    /** 1 µm = 39.37 µin */
    const val UIN_PER_UM = 39.37

    fun recommend(service: Service, strict: Boolean = false): List<Roughness> = when (service) {
        Service.DYNAMIC -> listOf(
            Roughness(
                Surface.CONTACT, 0.1, 0.4, 1.6,
                "滑動面；材料支承率 Mr > 50%（深度 0.5·Rz 處）。太光滑（Ra < 0.1）留不住油膜，摩擦反而變大",
            ),
            Roughness(Surface.GROOVE_BOTTOM, null, if (strict) 0.8 else 1.6, if (strict) 3.2 else 6.3, "不滑動，但 O 型環會在上面微動"),
            Roughness(Surface.GROOVE_FLANK, null, if (strict) 1.6 else 3.2, if (strict) 6.3 else 10.0, "壓力脈動時 O 型環會在側面來回移動"),
        )
        Service.STATIC -> listOf(
            Roughness(
                Surface.CONTACT, null, if (strict) 0.8 else 1.6, if (strict) 3.2 else 6.3,
                if (strict) "壓力脈動或氣體、真空密封，需較細的表面" else "一般液體、穩定壓力",
            ),
            Roughness(Surface.GROOVE_BOTTOM, null, 1.6, 6.3, "與 O 型環接觸的靜態面"),
            Roughness(Surface.GROOVE_FLANK, null, if (strict) 1.6 else 3.2, if (strict) 6.3 else 10.0, "壓力脈動時 O 型環會在側面來回移動"),
        )
    }
}
