package com.orings.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orings.core.Extrusion
import com.orings.core.Hardness
import com.orings.core.LengthUnit
import com.orings.core.Operating
import com.orings.core.PressureUnit
import java.util.Locale

/**
 * 壓力條件輸入（兩個設計分頁共用）。
 * 壓力留空 = 不考慮壓力；硬度、擋圈為 null = 自動（依推薦）。
 */
class OperatingInputState(
    pressureText: String = "",
    pressureUnit: PressureUnit = PressureUnit.MPA,
    gapText: String = "0.1",
    hardness: Hardness? = null,
    backupRings: Int? = null,
) {
    var pressureText by mutableStateOf(pressureText)
    var pressureUnit by mutableStateOf(pressureUnit)
    /** 擠出間隙，以目前長度單位表示 */
    var gapText by mutableStateOf(gapText)
    var hardness by mutableStateOf(hardness)
    var backupRings by mutableStateOf(backupRings)

    fun changeLengthUnit(from: LengthUnit, to: LengthUnit) {
        gapText = to.convertText(gapText, from)
    }

    fun changePressureUnit(to: PressureUnit) {
        val mpa = parse(pressureText)?.let(pressureUnit::toMpa)
        if (mpa != null) pressureText = formatPressure(to.fromMpa(mpa), to)
        pressureUnit = to
    }

    /** null = 未輸入壓力；輸入無效時丟出例外 */
    fun pressureMpa(): Double? {
        if (pressureText.isBlank()) return null
        val v = parse(pressureText) ?: throw IllegalArgumentException("請輸入有效的壓力")
        return pressureUnit.toMpa(v)
    }

    fun gapMm(unit: LengthUnit): Double =
        unit.parseToMm(gapText) ?: throw IllegalArgumentException("請輸入有效的擠出間隙")

    fun recommendation(unit: LengthUnit): Extrusion.Recommendation? =
        runCatching { pressureMpa()?.let { Extrusion.recommend(it, gapMm(unit)) } }.getOrNull()

    /** 依目前輸入組出 [Operating]；未輸入壓力時為 null */
    fun operating(unit: LengthUnit): Operating? {
        val p = pressureMpa() ?: return null
        val gap = gapMm(unit)
        val rec = Extrusion.recommend(p, gap)
        return Operating(
            pressureMpa = p,
            radialGap = gap,
            hardness = hardness ?: rec.hardness ?: Hardness.A90,
            backupRings = backupRings ?: if (rec.needsBackup) 1 else 0,
        )
    }

    companion object {
        private fun parse(text: String) = text.trim().replace(",", ".").toDoubleOrNull()

        private fun formatPressure(v: Double, unit: PressureUnit) =
            String.format(Locale.US, "%.${unit.decimals}f", v).trimEnd('0').trimEnd('.')

        val Saver: Saver<OperatingInputState, Any> = listSaver(
            save = {
                listOf(it.pressureText, it.pressureUnit.name, it.gapText, it.hardness?.name ?: "", it.backupRings ?: -1)
            },
            restore = {
                OperatingInputState(
                    pressureText = it[0] as String,
                    pressureUnit = PressureUnit.valueOf(it[1] as String),
                    gapText = it[2] as String,
                    hardness = (it[3] as String).takeIf(String::isNotEmpty)?.let(Hardness::valueOf),
                    backupRings = (it[4] as Int).takeIf { n -> n >= 0 },
                )
            },
        )
    }
}

private val hardnessOptions: List<Hardness?> = listOf(null) + Hardness.entries
private val backupOptions: List<Int?> = listOf(null, 0, 1, 2)

/** 壓力、間隙、硬度、擋圈輸入區 */
@Composable
fun OperatingSection(state: OperatingInputState, unit: LengthUnit) {
    val rec = state.recommendation(unit)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("壓力與硬度（壓力留空則不檢查擠出）", style = MaterialTheme.typography.labelLarge)
        ChipRow(PressureUnit.entries, state.pressureUnit, { it.label }, state::changePressureUnit)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.pressureText,
                onValueChange = { state.pressureText = it },
                label = { Text("工作壓力（${state.pressureUnit.label}）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.gapText,
                onValueChange = { state.gapText = it },
                label = { Text("擠出間隙（單邊 ${unit.symbol}）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Text("硬度", style = MaterialTheme.typography.labelMedium)
        ChipRow(hardnessOptions, state.hardness, { h ->
            if (h == null) "自動" + (rec?.hardness?.let { "（${it.label}）" } ?: "") else "Shore ${h.label}"
        }) { state.hardness = it }
        Text("擋圈", style = MaterialTheme.typography.labelMedium)
        ChipRow(backupOptions, state.backupRings, { n ->
            when (n) {
                null -> "自動" + (rec?.let { if (it.needsBackup) "（單側）" else "（不用）" } ?: "")
                0 -> "不用"
                1 -> "單側"
                else -> "雙側"
            }
        }) { state.backupRings = it }
        if (rec != null) {
            Text(
                "建議：" + (rec.hardness?.let { "Shore ${it.label}" } ?: "不適用 O 型環") +
                    (if (rec.needsBackup && rec.hardness != null) " + 擋圈" else "") + "。" + rec.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (rec.hardness == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "※ 擠出極限為一般經驗值（靜態、室溫），正式設計請以廠商擠出曲線為準。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
