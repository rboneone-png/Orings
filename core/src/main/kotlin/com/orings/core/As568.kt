package com.orings.core

/**
 * AS568 標準尺寸（部分樣本）。原始資料為英吋，這裡換算成 mm。
 * TODO: 補齊完整尺寸表（建議改放 JSON / Room 資料庫）。
 */
object As568 {
    private const val INCH = 25.4

    data class Size(val dash: String, val idInch: Double, val csInch: Double) {
        fun toORing() = ORing(idInch * INCH, csInch * INCH)
    }

    val sizes: List<Size> = listOf(
        Size("-010", 0.239, 0.070),
        Size("-011", 0.301, 0.070),
        Size("-012", 0.364, 0.070),
        Size("-110", 0.362, 0.103),
        Size("-210", 0.734, 0.139),
        Size("-325", 1.475, 0.210),
    )

    fun find(dash: String): Size? {
        val key = if (dash.startsWith("-")) dash else "-$dash"
        return sizes.firstOrNull { it.dash == key }
    }
}
