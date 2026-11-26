package com.orings.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orings.core.Material
import com.orings.core.MaterialAdvisor
import com.orings.core.Medium
import com.orings.core.Rating
import com.orings.core.Service

/** 材質選擇：輸入溫度與介質，列出各材質是否適用 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialScreen(
    service: Service,
    onServiceChange: (Service) -> Unit,
    selected: Material?,
    onSelect: (Material) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tempMin by rememberSaveable { mutableStateOf("-20") }
    var tempMax by rememberSaveable { mutableStateOf("80") }
    // 存成名稱清單，rememberSaveable 才能保存
    var mediaNames by rememberSaveable { mutableStateOf(listOf(Medium.MINERAL_OIL.name)) }
    val media = mediaNames.map { Medium.valueOf(it) }.toSet()

    val tMin = tempMin.trim().toIntOrNull()
    val tMax = tempMax.trim().toIntOrNull()
    val evaluations = if (tMin != null && tMax != null && tMin <= tMax) {
        MaterialAdvisor.evaluate(tMin, tMax, media, service)
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("使用條件", style = MaterialTheme.typography.labelLarge)
        ChipRow(Service.entries, service, { it.label }, onServiceChange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TempField("最低溫度 °C", tempMin, { tempMin = it }, Modifier.weight(1f))
            TempField("最高溫度 °C", tempMax, { tempMax = it }, Modifier.weight(1f))
        }

        Text("接觸介質（可複選）", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Medium.entries.forEach { m ->
                FilterChip(
                    selected = m in media,
                    onClick = {
                        mediaNames = if (m in media) mediaNames - m.name else mediaNames + m.name
                    },
                    label = { Text(m.label) },
                )
            }
        }

        if (evaluations == null) {
            Text("請輸入有效的溫度範圍（整數，最低 ≤ 最高）", color = MaterialTheme.colorScheme.error)
        } else {
            Text("點選材質即套用到溝槽設計", style = MaterialTheme.typography.bodySmall)
            evaluations.forEach { e ->
                MaterialCard(e, media, isSelected = e.material.code == selected?.code) { onSelect(e.material) }
            }
        }

        Text(
            "※ 溫度與耐受性為一般工業經驗值，實際依配方差異很大，正式選用請以廠商型錄為準。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TempField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        // 溫度可能是負數，用一般文字鍵盤才打得出負號
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier,
    )
}

@Composable
private fun MaterialCard(
    e: MaterialAdvisor.Evaluation,
    media: Set<Medium>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val m = e.material
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = when {
                !e.suitable -> MaterialTheme.colorScheme.surfaceVariant
                e.cautions.isEmpty() -> Color(0xFFDDF3E4)
                else -> Color(0xFFFFF1CC)
            },
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    (if (e.suitable) "✓ " else "✗ ") + m.label + (if (isSelected) "（已選）" else ""),
                    fontWeight = FontWeight.Bold,
                )
                Text("${m.tempMinC}～${m.tempMaxC}°C")
            }
            Text(m.note, style = MaterialTheme.typography.bodySmall)
            if (media.isNotEmpty()) {
                Text(
                    media.joinToString("　") { "${it.label}：${ratingMark(m.rating(it))}" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            (e.problems.map { "✗ $it" } + e.cautions.map { "! $it" }).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("✗")) MaterialTheme.colorScheme.error else Color(0xFF8A5A00),
                )
            }
        }
    }
}

private fun ratingMark(r: Rating) = when (r) {
    Rating.GOOD -> "◎${r.label}"
    Rating.FAIR -> "△${r.label}"
    Rating.POOR -> "✗${r.label}"
}
