package com.orings.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orings.core.HardwareMaterial
import com.orings.core.Material
import com.orings.core.Severity
import com.orings.core.Thermal
import java.util.Locale

/** 工作溫度與金屬件材質輸入 */
@Composable
fun ThermalInputSection(
    tMin: String,
    onTMin: (String) -> Unit,
    tMax: String,
    onTMax: (String) -> Unit,
    hardware: HardwareMaterial,
    onHardware: (HardwareMaterial) -> Unit,
    material: Material?,
) {
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
        Text("金屬件材質", style = MaterialTheme.typography.labelMedium)
        ChipRow(HardwareMaterial.entries, hardware, { it.label }, onHardware)
        Text(
            "O 型環膨脹係數：" + (material?.let { "${it.code} " } ?: "NBR（未選材質，預設）") +
                String.format(Locale.US, "%.1f×10⁻⁴/°C", (material?.cte ?: 1.3e-4) * 1e4) +
                String.format(Locale.US, "；金屬 %.1f×10⁻⁶/°C", hardware.cte * 1e6),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 熱膨脹修正結果：組裝、最低溫、最高溫三個狀態 */
@Composable
fun ThermalCard(analysis: Thermal.Analysis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (analysis.isOk) "熱膨脹修正：各溫度皆無錯誤" else "熱膨脹修正：有溫度不合格",
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
