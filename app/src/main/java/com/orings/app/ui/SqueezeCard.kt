package com.orings.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orings.core.LengthUnit
import com.orings.core.Severity
import com.orings.core.Squeeze
import java.util.Locale

/** 預壓量的公差輸入 */
@Composable
fun SqueezeInputSection(
    unit: LengthUnit,
    csTolText: String,
    onCsTol: (String) -> Unit,
    autoCsTol: Double?,
    depthTolText: String,
    onDepthTol: (String) -> Unit,
    eccentric: Boolean,
    onEccentric: (Boolean) -> Unit,
    showEccentric: Boolean,
    /** false：只輸入深度公差，線徑公差依各尺寸自動（O 型環設計分頁用） */
    showCsTolerance: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("預壓量公差（${unit.symbol}）", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showCsTolerance) OutlinedTextField(
                value = csTolText,
                onValueChange = onCsTol,
                label = { Text("線徑公差 ±") },
                placeholder = { Text(autoCsTol?.let { "自動 ${unit.format(it)}" } ?: "自動") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = depthTolText,
                onValueChange = onDepthTol,
                label = { Text("溝槽深度公差 ±") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        if (showEccentric) {
            FilterChip(
                selected = eccentric,
                onClick = { onEccentric(!eccentric) },
                label = { Text("計入偏心（偏心量 = 擠出間隙）") },
            )
        }
        Text(
            if (showCsTolerance) "線徑公差留空時依 AS568／JIS 規格自動帶入。"
            else "線徑公差依各尺寸的 AS568／JIS 規格自動帶入；深度公差留空則不判定預壓量。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 預壓量結果：名目與公差最差情況 */
@Composable
fun SqueezeCard(r: Squeeze.Result, unit: LengthUnit, title: String = "預壓量") {
    fun mmPct(mm: Double, pct: Double) = "${unit.format(mm)} ${unit.symbol}（${String.format(Locale.US, "%.1f%%", pct * 100)}）"
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !r.isOk -> MaterialTheme.colorScheme.errorContainer
                r.issues.isEmpty() -> Color(0xFFDDF3E4)
                else -> Color(0xFFFFF1CC)
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    !r.isOk -> "✗ ${title}不合格"
                    r.issues.isEmpty() -> "✓ ${title}在公差範圍內皆合格"
                    else -> "! ${title}有警告"
                },
                fontWeight = FontWeight.Bold,
            )
            Row2("預壓量（名目）", mmPct(r.nominalMm, r.nominalPct))
            Row2("最小（公差最差）", mmPct(r.minMm, r.minPct))
            Row2("最大（公差最差）", mmPct(r.maxMm, r.maxPct))
            Row2(
                "採用公差",
                "線徑 ±${unit.format(r.csTolerance)}、深度 ±${unit.format(r.depthTolerance)}" +
                    if (r.eccentricity > 0) "、偏心 ${unit.format(r.eccentricity)}" else "",
            )
            r.issues.forEach {
                Text(
                    (if (it.severity == Severity.ERROR) "✗ " else "! ") + it.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.severity == Severity.ERROR) MaterialTheme.colorScheme.error else Color(0xFF8A5A00),
                )
            }
            Text(
                "※ 最小預壓：線徑取下限、深度取上限（含偏心）；最大預壓反之。判定門檻為一般經驗值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
