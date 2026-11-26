package com.orings.core

import kotlin.math.round

/**
 * JIS B2401 標準尺寸表：P 系列（運動用）與 G 系列（固定用）。
 *
 * JIS 規格的內徑 = 公稱值 − 固定差值，差值依線徑而定：
 * - P：CS 1.9 / 2.4 → −0.2；CS 3.5 → −0.3；CS 5.7 → −0.4；CS 8.4 → −0.5
 * - G：CS 3.1 → −0.6；CS 5.7 → −0.7
 * 帶 "A" 的代號（P10A、P22A…）是同一公稱值換大一號線徑。
 * 注意：數值依標準整理，正式使用前請與 JIS B2401 原文或廠商型錄核對。
 */
object JisB2401 {

    private fun series(
        standard: Standard,
        prefix: String,
        cs: Double,
        offset: Double,
        vararg nominals: String,
    ): List<StandardSize> = nominals.map { n ->
        val nominal = n.removeSuffix("A").toDouble()
        StandardSize(
            standard = standard,
            code = prefix + n,
            innerDiameter = round((nominal - offset) * 100) / 100,
            crossSection = cs,
        )
    }

    private fun range(from: Int, to: Int, step: Int) =
        (from..to step step).map { it.toString() }.toTypedArray()

    val pSeries: List<StandardSize> =
        series(Standard.JIS_P, "P", 1.9, 0.2, "3", "4", "5", "6", "7", "8", "9", "10") +
            series(
                Standard.JIS_P, "P", 2.4, 0.2,
                "10A", "11", "11.2", "12", "12.5", "14", "15", "16", "18", "20", "21", "22",
            ) +
            series(
                Standard.JIS_P, "P", 3.5, 0.3,
                "22A", "22.4", "24", "25", "25.5", "26", "28", "29", "29.5", "30",
                "31", "31.5", "32", "34", "35", "35.5", "36", "38", "39", "40",
                "41", "42", "44", "45", "46", "48", "49", "50",
            ) +
            series(
                Standard.JIS_P, "P", 5.7, 0.4,
                "48A", "50A", "52", "53", "55", "56", "58", "60", "62", "63",
                "65", "67", "70", "71", "75", "80", "85", "90", "95", "100",
                "102", "105", "110", "112", "115", "120", "125", "130", "132", "135",
                "140", "145", "150",
            ) +
            series(
                Standard.JIS_P, "P", 8.4, 0.5,
                "150A", "155", "160", "165", "170", "175", "180", "185", "190", "195",
                "200", "205", "209", "210", "215", "220", "225", "230", "235", "240",
                "245", "250", "255", "260", "265", "270", "275", "280", "285", "290",
                "295", "300", "315", "320", "335", "340", "355", "360", "375", "385", "400",
            )

    val gSeries: List<StandardSize> =
        series(Standard.JIS_G, "G", 3.1, 0.6, *range(25, 145, 5)) +
            series(Standard.JIS_G, "G", 5.7, 0.7, *range(150, 300, 5))

    val sizes: List<StandardSize> = pSeries + gSeries

    fun find(code: String): StandardSize? {
        val key = code.trim().uppercase()
        return sizes.firstOrNull { it.code == key }
    }
}
