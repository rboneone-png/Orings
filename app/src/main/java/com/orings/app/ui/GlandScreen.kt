package com.orings.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orings.core.Gland
import com.orings.core.GlandCalculator
import com.orings.core.GlandResult
import com.orings.core.ORing
import com.orings.core.SealType
import com.orings.core.Service
import com.orings.core.Severity

@Composable
fun GlandScreen() {
    var sealType by rememberSaveable { mutableStateOf(SealType.PISTON) }
    var service by rememberSaveable { mutableStateOf(Service.STATIC) }
    var ringId by rememberSaveable { mutableStateOf("20.0") }
    var ringCs by rememberSaveable { mutableStateOf("3.53") }
    var mateDia by rememberSaveable { mutableStateOf("26.0") }
    var grooveDia by rememberSaveable { mutableStateOf("20.5") }
    var grooveWidth by rememberSaveable { mutableStateOf("4.8") }
    var faceDepth by rememberSaveable { mutableStateOf("2.7") }

    var parsed by remember { mutableStateOf<GlandResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun d(s: String) = s.trim().toDoubleOrNull() ?: throw IllegalArgumentException("請輸入有效數字")

    fun ring() = ORing(d(ringId), d(ringCs))

    fun calculate() {
        runCatching {
            val gland = when (sealType) {
                SealType.FACE -> Gland(sealType, d(grooveWidth), d(grooveDia), faceDepth = d(faceDepth))
                else -> Gland(sealType, d(grooveWidth), d(grooveDia), d(mateDia))
            }
            GlandCalculator.calculate(ring(), gland, service)
        }.onSuccess { parsed = it; error = null }
            .onFailure { parsed = null; error = it.message }
    }

    fun suggest() {
        runCatching {
            val mate = if (sealType == SealType.FACE) 0.0 else d(mateDia)
            GlandCalculator.suggest(ring(), sealType, service, mate)
        }.onSuccess { g ->
            grooveWidth = "%.2f".format(g.grooveWidth)
            grooveDia = "%.2f".format(g.grooveDiameter)
            if (sealType == SealType.FACE) faceDepth = "%.2f".format(g.faceDepth)
            calculate()
        }.onFailure { error = it.message }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("O 型環溝槽設計", style = MaterialTheme.typography.headlineSmall)

            Text("密封型式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SealType.entries.forEach {
                    FilterChip(selected = sealType == it, onClick = { sealType = it }, label = { Text(it.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Service.entries.forEach {
                    FilterChip(selected = service == it, onClick = { service = it }, label = { Text(it.label) })
                }
            }

            Text("O 型環（mm）", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("內徑 ID", ringId, { ringId = it }, Modifier.weight(1f))
                NumberField("線徑 CS", ringCs, { ringCs = it }, Modifier.weight(1f))
            }

            Text("溝槽（mm）", style = MaterialTheme.typography.labelLarge)
            when (sealType) {
                SealType.PISTON -> {
                    NumberField("缸筒內徑", mateDia, { mateDia = it })
                    NumberField("溝槽底徑（活塞上）", grooveDia, { grooveDia = it })
                }
                SealType.ROD -> {
                    NumberField("軸桿外徑", mateDia, { mateDia = it })
                    NumberField("溝槽底徑（殼體內）", grooveDia, { grooveDia = it })
                }
                SealType.FACE -> {
                    NumberField("溝槽內徑", grooveDia, { grooveDia = it })
                    NumberField("溝槽深度", faceDepth, { faceDepth = it })
                }
            }
            NumberField("溝槽寬度", grooveWidth, { grooveWidth = it })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::calculate, modifier = Modifier.weight(1f)) { Text("計算") }
                OutlinedButton(onClick = ::suggest, modifier = Modifier.weight(1f)) { Text("推薦溝槽") }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            parsed?.let { ResultCard(it) }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun ResultCard(r: GlandResult) {
    val ok = r.isOk && r.issues.isEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !r.isOk -> MaterialTheme.colorScheme.errorContainer
                ok -> Color(0xFFDDF3E4)
                else -> Color(0xFFFFF1CC)
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    !r.isOk -> "✗ 設計不合格"
                    ok -> "✓ 設計合格"
                    else -> "! 可用，但有警告"
                },
                fontWeight = FontWeight.Bold,
            )
            ResultRow("溝槽深度", "%.3f mm".format(r.depth))
            ResultRow("有效線徑", "%.3f mm".format(r.effectiveCrossSection))
            ResultRow(
                "壓縮率",
                "%.1f%%（建議 %.0f–%.0f%%）".format(
                    r.compression * 100, r.limits.minCompression * 100, r.limits.maxCompression * 100,
                ),
            )
            ResultRow("拉伸率", "%.1f%%（建議 ≤ %.0f%%）".format(r.stretch * 100, r.limits.maxStretch * 100))
            ResultRow("填充率", "%.1f%%（建議 ≤ %.0f%%）".format(r.fill * 100, r.limits.maxFill * 100))
            r.issues.forEach {
                Text(
                    (if (it.severity == Severity.ERROR) "✗ " else "! ") + it.message,
                    color = if (it.severity == Severity.ERROR) MaterialTheme.colorScheme.error else Color(0xFF8A5A00),
                )
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
private fun GlandScreenPreview() {
    MaterialTheme { GlandScreen() }
}
