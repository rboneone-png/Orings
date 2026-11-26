package com.orings.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orings.core.LengthUnit
import com.orings.core.Roughness
import com.orings.core.Service
import com.orings.core.SurfaceFinish
import java.util.Locale

/** 粗糙度數值：公制 µm（一位小數），英制 µin（整數） */
fun formatRoughness(um: Double, unit: LengthUnit): String = when (unit) {
    LengthUnit.MM -> String.format(Locale.US, "%.1f", um)
    LengthUnit.INCH -> String.format(Locale.US, "%.0f", um * SurfaceFinish.UIN_PER_UM)
}

fun roughnessSymbol(unit: LengthUnit) = if (unit == LengthUnit.MM) "µm" else "µin"

fun roughnessText(r: Roughness, unit: LengthUnit): String {
    val sym = roughnessSymbol(unit)
    val ra = r.raMin?.let { "Ra ${formatRoughness(it, unit)}–${formatRoughness(r.raMax, unit)}" }
        ?: "Ra ≤ ${formatRoughness(r.raMax, unit)}"
    return "$ra $sym（Rz ≤ ${formatRoughness(r.rzMax, unit)}）"
}

/** 表面粗糙度建議 */
@Composable
fun SurfaceFinishCard(
    service: Service,
    unit: LengthUnit,
    strict: Boolean,
    onStrictChange: (Boolean) -> Unit,
) {
    val rows = SurfaceFinish.recommend(service, strict)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("表面粗糙度建議（${service.label}）", fontWeight = FontWeight.Bold)
            FilterChip(
                selected = strict,
                onClick = { onStrictChange(!strict) },
                label = { Text("壓力脈動／氣體或真空") },
            )
            rows.forEach { r ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.surface.label, modifier = Modifier.weight(1f))
                    }
                    Text(roughnessText(r, unit), fontWeight = FontWeight.Medium)
                    Text(r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
            Text(
                "※ 一般密封件廠商設計指引的經驗值，正式設計請以廠商型錄為準。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
