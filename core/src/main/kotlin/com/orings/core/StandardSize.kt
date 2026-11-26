package com.orings.core

/** O 型環尺寸標準 */
enum class Standard(val label: String) {
    AS568("AS568（美規）"),
    JIS_P("JIS B2401 P（運動用）"),
    JIS_G("JIS B2401 G（固定用）"),
}

/**
 * 標準 O 型環尺寸。長度單位 mm；AS568 另保留原始英吋值。
 */
data class StandardSize(
    val standard: Standard,
    /** 規格代號，例如 "-210"、"P22"、"G50" */
    val code: String,
    val innerDiameter: Double,
    val crossSection: Double,
    val idInch: Double? = null,
    val csInch: Double? = null,
) {
    fun toORing() = ORing(innerDiameter, crossSection)

    /** AS568 9xx：直螺紋接頭埠口專用，不適合一般溝槽設計 */
    val isPortSeal: Boolean get() = standard == Standard.AS568 && code.startsWith("-9")

    /** 該尺寸是否適合此使用條件（JIS G 系列為固定用，不建議動態） */
    fun suitableForService(service: Service): Boolean =
        !(service == Service.DYNAMIC && standard == Standard.JIS_G)

    /** 顯示用名稱，例如 "AS568 -210" */
    val displayName: String
        get() = when (standard) {
            Standard.AS568 -> "AS568 $code"
            Standard.JIS_P, Standard.JIS_G -> "JIS $code"
        }
}
