package com.orings.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orings.core.Gland
import com.orings.core.LengthUnit
import com.orings.core.RingDesigner
import com.orings.core.SealType
import com.orings.core.Service
import com.orings.core.Severity
import com.orings.core.StandardSize

/**
 * O 型環設計：已知溝槽，反推理想 O 型環、推薦硬度，並列出合適的標準尺寸。
 */
@Composable
fun RingDesignScreen(
    unit: LengthUnit,
    service: Service,
    onServiceChange: (Service) -> Unit,
    operatingState: OperatingInputState,
    onPick: (StandardSize, Gland) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sealType by rememberSaveable { mutableStateOf(SealType.PISTON) }
    var mateDia by rememberSaveable { mutableStateOf(unit.format(26.0)) }
    var grooveDia by rememberSaveable { mutableStateOf(unit.format(20.5)) }
    var grooveWidth by rememberSaveable { mutableStateOf(unit.format(4.8)) }
    var faceDepth by rememberSaveable { mutableStateOf(unit.format(2.7)) }

    var fieldsUnit by rememberSaveable { mutableStateOf(unit) }
    LaunchedEffect(unit) {
        if (fieldsUnit != unit) {
            mateDia = unit.convertText(mateDia, fieldsUnit)
            grooveDia = unit.convertText(grooveDia, fieldsUnit)
            grooveWidth = unit.convertText(grooveWidth, fieldsUnit)
            faceDepth = unit.convertText(faceDepth, fieldsUnit)
            fieldsUnit = unit
        }
    }

    fun d(s: String) = fieldsUnit.parseToMm(s) ?: throw IllegalArgumentException("請輸入有效數字")

    // 每次輸入改變就重新計算（標準尺寸約 500 筆，計算很快）
    val outcome = runCatching {
        val gland = when (sealType) {
            SealType.FACE -> Gland(sealType, d(grooveWidth), d(grooveDia), faceDepth = d(faceDepth))
            else -> Gland(sealType, d(grooveWidth), d(grooveDia), d(mateDia))
        }
        val op = operatingState
        val gap = if (op.pressureMpa() != null) op.gapMm(unit) else 0.1
        gland to RingDesigner.design(
            gland = gland,
            service = service,
            pressureMpa = op.pressureMpa(),
            radialGap = gap,
            hardness = op.hardness,
            backupRings = op.backupRings,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("輸入現有溝槽，反推合適的 O 型環", style = MaterialTheme.typography.bodyMedium)

        Text("密封型式", style = MaterialTheme.typography.labelLarge)
        ChipRow(SealType.entries, sealType, { it.label }) { sealType = it }
        ChipRow(Service.entries, service, { it.label }, onServiceChange)

        Text("溝槽（${unit.symbol}）", style = MaterialTheme.typography.labelLarge)
        when (sealType) {
            SealType.PISTON -> {
                Field("缸筒內徑", mateDia) { mateDia = it }
                Field("溝槽底徑（活塞上）", grooveDia) { grooveDia = it }
            }
            SealType.ROD -> {
                Field("軸桿外徑", mateDia) { mateDia = it }
                Field("溝槽底徑（殼體內）", grooveDia) { grooveDia = it }
            }
            SealType.FACE -> {
                Field("溝槽內徑", grooveDia) { grooveDia = it }
                Field("溝槽深度", faceDepth) { faceDepth = it }
            }
        }
        Field("溝槽寬度", grooveWidth) { grooveWidth = it }

        OperatingSection(operatingState, unit)

        outcome.onFailure { Text(it.message ?: "輸入有誤", color = MaterialTheme.colorScheme.error) }
        outcome.onSuccess { (gland, design) ->
            IdealCard(design, unit)
            MatchList(design, unit) { size -> onPick(size, gland) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IdealCard(design: RingDesigner.Design, unit: LengthUnit) {
    val r = design.idealResult
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("理想 O 型環（非標準尺寸）", fontWeight = FontWeight.Bold)
            Row2("內徑 ID", "${unit.format(design.idealInnerDiameter)} ${unit.symbol}")
            Row2("線徑 CS", "${unit.format(design.idealCrossSection)} ${unit.symbol}")
            Row2(
                "目標壓縮／拉伸",
                "%.1f%% ／ %.1f%%".format(design.targetCompression * 100, design.targetStretch * 100),
            )
            Row2("填充率", "%.1f%%".format(r.fill * 100))
            design.hardness?.let { h ->
                Row2(
                    "建議硬度",
                    (h.hardness?.let { "Shore ${it.label}" } ?: "不適用") + if (h.needsBackup && h.hardness != null) " + 擋圈" else "",
                )
            }
            r.issues.forEach {
                Text(
                    (if (it.severity == Severity.ERROR) "✗ " else "! ") + it.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.severity == Severity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun MatchList(design: RingDesigner.Design, unit: LengthUnit, onPick: (StandardSize) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("合適的標準尺寸（點選帶入溝槽設計）", fontWeight = FontWeight.Bold)
            if (design.matches.isEmpty()) {
                Text(
                    "沒有不出錯的標準尺寸，請調整溝槽、硬度或加擋圈。",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            design.matches.forEach { m ->
                val warnings = m.result.issues.count { it.severity == Severity.WARNING }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(m.size) }
                        .padding(vertical = 6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.size.displayName, fontWeight = FontWeight.Medium)
                        Text("ID ${unit.format(m.size.innerDiameter)} × CS ${unit.format(m.size.crossSection)}")
                    }
                    Text(
                        "壓縮 %.1f%%  拉伸 %.1f%%  填充 %.1f%%".format(
                            m.result.compression * 100, m.result.stretch * 100, m.result.fill * 100,
                        ) + if (warnings > 0) "　! $warnings 項警告" else "　✓",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
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
