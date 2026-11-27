package com.orings.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.remember
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orings.core.HardwareCategory
import com.orings.core.HardwareMaterial
import com.orings.core.Material
import com.orings.core.Severity
import com.orings.core.Thermal
import com.orings.core.ThermalInput
import java.util.Locale

/**
 * 工作溫度與金屬件材質（溝槽設計與 O型環設計共用）。
 * 兩個溫度都空白時不做熱膨脹修正。
 */
class ThermalInputState(
    tMinText: String = "-20",
    tMaxText: String = "80",
    hardware: HardwareMaterial = HardwareMaterial.STEEL,
) {
    var tMinText by mutableStateOf(tMinText)
    var tMaxText by mutableStateOf(tMaxText)
    var hardware by mutableStateOf(hardware)

    /** 溫度欄位空白時回傳 null；輸入無效時丟出錯誤 */
    fun input(material: Material?): ThermalInput? {
        if (tMinText.isBlank() && tMaxText.isBlank()) return null
        val lo = tMinText.trim().toDoubleOrNull() ?: throw IllegalArgumentException("請輸入有效的最低溫度")
        val hi = tMaxText.trim().toDoubleOrNull() ?: throw IllegalArgumentException("請輸入有效的最高溫度")
        return ThermalInput.of(lo, hi, material, hardware)
    }

    companion object {
        val Saver: Saver<ThermalInputState, Any> = listSaver(
            save = { listOf(it.tMinText, it.tMaxText, it.hardware.name) },
            restore = { ThermalInputState(it[0], it[1], HardwareMaterial.valueOf(it[2])) },
        )
    }
}

/** 工作溫度與金屬件材質輸入 */
@Composable
fun ThermalInputSection(state: ThermalInputState, material: Material?) {
    val tMin = state.tMinText
    val tMax = state.tMaxText
    val hardware = state.hardware
    val onTMin: (String) -> Unit = { state.tMinText = it }
    val onTMax: (String) -> Unit = { state.tMaxText = it }
    val onHardware: (HardwareMaterial) -> Unit = { state.hardware = it }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("工作溫度與熱膨脹", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = tMin, onValueChange = onTMin, label = { Text("最低溫度 °C") }, singleLine = true,
                // 溫度可能是負數，用一般文字鍵盤才打得出負號
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = tMax, onValueChange = onTMax, label = { Text("最高溫度 °C") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
        }
        Text("溝槽件材質（缸筒、活塞、殼體）", style = MaterialTheme.typography.labelMedium)
        HardwareDropdown(hardware, onHardware)
        Text(
            "O 型環膨脹係數：" + (material?.let { "${it.code} " } ?: "NBR（未選材質，預設）") +
                String.format(Locale.US, "%.1f×10⁻⁴/°C", (material?.cte ?: 1.3e-4) * 1e4) +
                String.format(Locale.US, "；溝槽件 %.1f×10⁻⁶/°C", hardware.cte * 1e6),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 溝槽件材質下拉選單：依金屬、塑膠分組，顯示膨脹係數 */
@Composable
fun HardwareDropdown(selected: HardwareMaterial, onSelect: (HardwareMaterial) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    fun cteText(h: HardwareMaterial) = String.format(Locale.US, "%.1f×10⁻⁶/°C", h.cte * 1e6)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "${selected.category.label}｜${selected.label}　${cteText(selected)}　▾",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HardwareCategory.entries.forEach { cat ->
                Text(
                    cat.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HardwareMaterial.entries.filter { it.category == cat }.forEach { h ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (h == selected) "✓ " else "　") + h.label + "　" + cteText(h),
                                fontWeight = if (h == selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = { onSelect(h); expanded = false },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/** 熱膨脹修正結果：組裝、最低溫、最高溫三個狀態 */
@Composable
fun ThermalCard(analysis: Thermal.Analysis, title: String = "熱膨脹修正") {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (analysis.isOk) "$title：各溫度皆無錯誤" else "$title：有溫度不合格",
                fontWeight = FontWeight.Bold,
                color = if (analysis.isOk) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Row(Modifier.fillMaxWidth()) {
                listOf("溫度", "壓縮", "拉伸", "填充").forEach {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                }
            }
            analysis.points.forEach { p ->
                val r = p.result
                Row(Modifier.fillMaxWidth()) {
                    Text("${p.label} ${fmt0(p.tempC)}°C", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    listOf(r.compression, r.stretch, r.fill).forEach {
                        Text(String.format(Locale.US, "%.1f%%", it * 100), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HorizontalDivider()
            // 組裝狀態的問題已顯示在上方結果，這裡只列高低溫的問題
            analysis.points.drop(1).forEach { p ->
                p.result.issues.forEach {
                    Text(
                        (if (it.severity == Severity.ERROR) "✗ " else "! ") + "${p.label} ${fmt0(p.tempC)}°C：${it.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.severity == Severity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            analysis.notes.forEach {
                Text("! $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Text(
                "※ 膨脹係數為一般手冊近似值；未考慮低溫硬化、介質膨潤與壓縮永久變形。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun fmt0(v: Double) = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
