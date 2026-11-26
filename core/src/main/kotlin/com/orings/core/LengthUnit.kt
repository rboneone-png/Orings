package com.orings.core

import java.util.Locale

/** 長度單位。計算核心一律使用 mm，只在輸入與顯示時換算。 */
enum class LengthUnit(val label: String, val symbol: String, val mmPerUnit: Double, val decimals: Int) {
    MM("公制 mm", "mm", 1.0, 3),
    INCH("英制 inch", "in", 25.4, 4),
    ;

    fun toMm(value: Double): Double = value * mmPerUnit

    fun fromMm(mm: Double): Double = mm / mmPerUnit

    /** 格式化 mm 值為此單位的字串，去掉多餘的 0，例如 25.4 mm → "1"（inch） */
    fun format(mm: Double): String =
        String.format(Locale.US, "%.${decimals}f", fromMm(mm)).trimEnd('0').trimEnd('.')

    /** 解析此單位的輸入字串（接受逗號小數點）並回傳 mm；無效時回傳 null */
    fun parseToMm(text: String): Double? =
        text.trim().replace(",", ".").toDoubleOrNull()?.let(::toMm)

    /** 把以 [from] 單位輸入的文字換成本單位的文字；無法解析時原樣回傳 */
    fun convertText(text: String, from: LengthUnit): String =
        from.parseToMm(text)?.let(::format) ?: text
}
