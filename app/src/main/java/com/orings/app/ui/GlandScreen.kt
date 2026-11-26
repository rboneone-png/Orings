package com.orings.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.orings.core.DesignRecord
import com.orings.core.Gland
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.orings.core.Thermal
import com.orings.core.ThermalInput
import com.orings.core.GlandCalculator
import com.orings.core.GlandResult
import com.orings.core.LengthUnit
import com.orings.core.Material
import com.orings.core.Materials
import com.orings.core.ORing
import com.orings.core.SealType
import com.orings.core.Service
import com.orings.core.Severity
import com.orings.core.SizeCatalog
import com.orings.core.StandardSize

/**
 * 溝槽設計畫面。輸入欄位以 [unit] 顯示，內部計算一律換成 mm。
 */
@Composable
fun GlandScreen(
    unit: LengthUnit,
    service: Service,
    onServiceChange: (Service) -> Unit,
    material: Material?,
    operatingState: OperatingInputState,
    /** 從「O型環設計」分頁帶入的尺寸與溝槽 */
    incoming: Pair<StandardSize, Gland>?,
    onIncomingConsumed: () -> Unit,
    thermalState: ThermalInputState,
    strictSurface: Boolean,
    onStrictSurfaceChange: (Boolean) -> Unit,
    /** 儲存目前的設計 */
    onSaveRecord: (DesignRecord) -> Unit,
    /** 從「存檔」分頁開啟的設計 */
    incomingRecord: DesignRecord?,
    onRecordConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sealType by rememberSaveable { mutableStateOf(SealType.PISTON) }
    // 文字欄位依目前單位儲存；切換單位時一起換算
    var ringId by rememberSaveable { mutableStateOf(unit.format(20.0)) }
    var ringCs by rememberSaveable { mutableStateOf(unit.format(3.53)) }
    var mateDia by rememberSaveable { mutableStateOf(unit.format(26.0)) }
    var grooveDia by rememberSaveable { mutableStateOf(unit.format(20.5)) }
    var grooveWidth by rememberSaveable { mutableStateOf(unit.format(4.8)) }
    var faceDepth by rememberSaveable { mutableStateOf(unit.format(2.7)) }

    var parsed by remember { mutableStateOf<GlandResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var sizeLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var thermalAnalysis by remember { mutableStateOf<Thermal.Analysis?>(null) }
    var candidates by remember { mutableStateOf<List<SizeCatalog.Candidate>?>(null) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    val exportPdf = rememberPdfExport()
    val context = LocalContext.current

    /** 解析輸入（目前單位）→ mm */
    fun d(s: String) = unit.parseToMm(s) ?: throw IllegalArgumentException("請輸入有效數字")

    /** mm → 目前單位的文字 */
    fun fmt(mm: Double) = unit.format(mm)

    fun ring() = ORing(d(ringId), d(ringCs))

    /** 溫度欄位空白時不做熱膨脹修正；輸入無效時丟出錯誤 */
    fun thermalInput(): ThermalInput? {
        return thermalState.input(material)
    }

    // 單位在最上方切換；欄位文字記錄自己是用哪個單位寫的，單位改變時一起換算
    var fieldsUnit by rememberSaveable { mutableStateOf(unit) }
    LaunchedEffect(unit) {
        if (fieldsUnit != unit) {
            ringId = unit.convertText(ringId, fieldsUnit)
            ringCs = unit.convertText(ringCs, fieldsUnit)
            mateDia = unit.convertText(mateDia, fieldsUnit)
            grooveDia = unit.convertText(grooveDia, fieldsUnit)
            grooveWidth = unit.convertText(grooveWidth, fieldsUnit)
            faceDepth = unit.convertText(faceDepth, fieldsUnit)
            fieldsUnit = unit
            parsed = null
        }
    }

    fun calculate() {
        runCatching {
            val gland = when (sealType) {
                SealType.FACE -> Gland(sealType, d(grooveWidth), d(grooveDia), faceDepth = d(faceDepth))
                else -> Gland(sealType, d(grooveWidth), d(grooveDia), d(mateDia))
            }
            val op = operatingState.operating(unit)
            val result = GlandCalculator.calculate(ring(), gland, service, operating = op)
            // 沒選材質時以預設的 NBR 計算膨脹，也用 NBR 的溫度範圍提醒
            thermalAnalysis = thermalInput()?.let {
                Thermal.analyze(ring(), gland, it, service, op, material ?: Materials.find("NBR"))
            }
            result
        }.onSuccess { parsed = it; error = null }
            .onFailure { parsed = null; thermalAnalysis = null; error = it.message }
    }

    fun suggest() {
        runCatching {
            val mate = if (sealType == SealType.FACE) 0.0 else d(mateDia)
            GlandCalculator.suggest(
                ring(), sealType, service, mate,
                operating = operatingState.operating(unit), thermal = thermalInput(),
            )
        }.onSuccess { g ->
            grooveWidth = fmt(g.grooveWidth)
            grooveDia = fmt(g.grooveDiameter)
            if (sealType == SealType.FACE) faceDepth = fmt(g.faceDepth)
            calculate()
        }.onFailure { error = it.message }
    }

    fun pickSize(size: StandardSize) {
        ringId = fmt(size.innerDiameter)
        ringCs = fmt(size.crossSection)
        sizeLabel = size.displayName
        showPicker = false
    }

    fun findSizes() {
        runCatching {
            // 端面密封以「溝槽內徑」作為配合直徑
            val mate = if (sealType == SealType.FACE) d(grooveDia) else d(mateDia)
            SizeCatalog.suitableFor(sealType, service, mate)
        }.onSuccess { candidates = it; error = null }
            .onFailure { candidates = null; error = it.message }
    }

    fun pickCandidate(c: SizeCatalog.Candidate) {
        pickSize(c.size)
        grooveWidth = fmt(c.gland.grooveWidth)
        grooveDia = fmt(c.gland.grooveDiameter)
        if (sealType == SealType.FACE) faceDepth = fmt(c.gland.faceDepth)
        candidates = null
        calculate()
    }

    /** 把目前所有輸入組成一筆存檔；輸入無效時丟出錯誤 */
    fun buildRecord(name: String): DesignRecord {
        val pressure = operatingState.pressureMpa()
        val tMin = thermalState.tMinText.trim()
        val tMax = thermalState.tMaxText.trim()
        val noThermal = tMin.isEmpty() && tMax.isEmpty()
        return DesignRecord(
            name = name,
            savedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
            sealType = sealType,
            service = service,
            unit = unit,
            ringId = d(ringId),
            ringCs = d(ringCs),
            sizeLabel = sizeLabel,
            mateDiameter = if (sealType == SealType.FACE) 0.0 else d(mateDia),
            grooveDiameter = d(grooveDia),
            grooveWidth = d(grooveWidth),
            faceDepth = if (sealType == SealType.FACE) d(faceDepth) else 0.0,
            materialCode = material?.code,
            pressureMpa = pressure,
            pressureUnit = operatingState.pressureUnit,
            radialGap = if (pressure != null) operatingState.gapMm(unit) else (runCatching { operatingState.gapMm(unit) }.getOrNull() ?: 0.1),
            hardness = operatingState.hardness,
            backupRings = operatingState.backupRings,
            tMinC = if (noThermal) null else tMin.toDoubleOrNull() ?: throw IllegalArgumentException("請輸入有效的最低溫度"),
            tMaxC = if (noThermal) null else tMax.toDoubleOrNull() ?: throw IllegalArgumentException("請輸入有效的最高溫度"),
            hardware = thermalState.hardware,
            strictSurface = strictSurface,
        ).also { it.gland } // 先驗證溝槽尺寸合理
    }

    val defaultName = (sizeLabel ?: "自訂尺寸") + "　" + sealType.label.substringBefore("（")

    if (showSaveDialog) {
        SaveDesignDialog(defaultName, onDismiss = { showSaveDialog = false }) { name ->
            showSaveDialog = false
            runCatching { buildRecord(name) }
                .onSuccess {
                    onSaveRecord(it)
                    Toast.makeText(context, "已儲存「$name」", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error = it.message }
        }
    }

    // 從「存檔」分頁開啟：單位已由上層切成存檔時的單位
    LaunchedEffect(incomingRecord) {
        val rec = incomingRecord ?: return@LaunchedEffect
        sealType = rec.sealType
        ringId = rec.unit.format(rec.ringId)
        ringCs = rec.unit.format(rec.ringCs)
        sizeLabel = rec.sizeLabel
        mateDia = rec.unit.format(if (rec.sealType == SealType.FACE) 26.0 else rec.mateDiameter)
        grooveDia = rec.unit.format(rec.grooveDiameter)
        grooveWidth = rec.unit.format(rec.grooveWidth)
        faceDepth = rec.unit.format(if (rec.sealType == SealType.FACE) rec.faceDepth else 2.7)
        fieldsUnit = rec.unit
        candidates = null
        onRecordConsumed()
        calculate()
    }

    // 從「O型環設計」分頁帶入
    LaunchedEffect(incoming) {
        val (size, g) = incoming ?: return@LaunchedEffect
        sealType = g.sealType
        ringId = fmt(size.innerDiameter)
        ringCs = fmt(size.crossSection)
        sizeLabel = size.displayName
        grooveWidth = fmt(g.grooveWidth)
        grooveDia = fmt(g.grooveDiameter)
        if (g.sealType == SealType.FACE) faceDepth = fmt(g.faceDepth) else mateDia = fmt(g.mateDiameter)
        onIncomingConsumed()
        calculate()
    }

    if (showPicker) {
        SizePickerDialog(unit = unit, onDismiss = { showPicker = false }, onPick = ::pickSize)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("密封型式", style = MaterialTheme.typography.labelLarge)
        ChipRow(SealType.entries, sealType, { it.label }) { sealType = it }
        ChipRow(Service.entries, service, { it.label }, onServiceChange)

        MaterialBanner(material, service)

        Text("O 型環（${unit.symbol}）", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("內徑 ID", ringId, { ringId = it; sizeLabel = null }, Modifier.weight(1f))
            NumberField("線徑 CS", ringCs, { ringCs = it; sizeLabel = null }, Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showPicker = true }) { Text("從尺寸表選") }
            Text(sizeLabel ?: "自訂尺寸", style = MaterialTheme.typography.bodyMedium)
        }

        Text("溝槽（${unit.symbol}）", style = MaterialTheme.typography.labelLarge)
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

        OperatingSection(operatingState, unit)

        ThermalInputSection(thermalState, material)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::calculate, modifier = Modifier.weight(1f)) { Text("計算") }
            OutlinedButton(onClick = ::suggest, modifier = Modifier.weight(1f)) { Text("推薦溝槽") }
        }
        OutlinedButton(onClick = ::findSizes, modifier = Modifier.fillMaxWidth()) {
            Text("依配合直徑找適用標準尺寸")
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        candidates?.let { CandidateList(it, unit, ::pickCandidate) }
        parsed?.let { ResultCard(it, unit) }
        if (parsed != null) thermalAnalysis?.let { ThermalCard(it) }
        if (parsed != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showSaveDialog = true }, modifier = Modifier.weight(1f)) { Text("儲存設計") }
                OutlinedButton(
                    onClick = {
                        runCatching { buildRecord(defaultName) }
                            .onSuccess(exportPdf)
                            .onFailure { error = it.message }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("下載 PDF") }
            }
        }
        SurfaceFinishCard(service, unit, strictSurface, onStrictSurfaceChange)
    }
}

@Composable
fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach {
            FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text(label(it)) })
        }
    }
}

/** 顯示目前選用的材質；動態使用但材質不適合時提醒 */
@Composable
private fun MaterialBanner(material: Material?, service: Service) {
    val text = when {
        material == null -> "材質：未選擇（到「材質選擇」分頁挑選）"
        service == Service.DYNAMIC && !material.dynamicOk -> "材質：${material.label}　! 不適合動態使用"
        else -> "材質：${material.label}　${material.tempMinC}～${material.tempMaxC}°C"
    }
    val warn = material != null && service == Service.DYNAMIC && !material.dynamicOk
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
private fun ResultCard(r: GlandResult, unit: LengthUnit) {
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
            ResultRow("溝槽深度", "${unit.format(r.depth)} ${unit.symbol}")
            ResultRow("有效線徑", "${unit.format(r.effectiveCrossSection)} ${unit.symbol}")
            ResultRow(
                "壓縮率",
                "%.1f%%（建議 %.0f–%.0f%%）".format(
                    r.compression * 100, r.limits.minCompression * 100, r.limits.maxCompression * 100,
                ),
            )
            ResultRow("拉伸率", "%.1f%%（建議 ≤ %.0f%%）".format(r.stretch * 100, r.limits.maxStretch * 100))
            ResultRow("填充率", "%.1f%%（建議 ≤ %.0f%%）".format(r.fill * 100, r.limits.maxFill * 100))
            r.extrusionLimit?.let { ResultRow("擠出極限", "%.1f MPa".format(it)) }
            if (r.extrusionLimit != null) {
                ResultRow("扣擋圈後溝槽寬", "${unit.format(r.effectiveWidth)} ${unit.symbol}")
            }
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
