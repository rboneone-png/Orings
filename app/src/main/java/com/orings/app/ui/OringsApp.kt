package com.orings.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orings.core.Gland
import com.orings.core.LengthUnit
import com.orings.core.Materials
import com.orings.core.Service
import com.orings.core.StandardSize

private val tabs = listOf("溝槽設計", "O型環設計", "材質選擇")

/** App 主框架：分頁、共用狀態（單位、使用條件、壓力條件、選用材質） */
@Composable
fun OringsApp() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var unit by rememberSaveable { mutableStateOf(LengthUnit.MM) }
    var service by rememberSaveable { mutableStateOf(Service.STATIC) }
    var materialCode by rememberSaveable { mutableStateOf<String?>(null) }
    val material = materialCode?.let(Materials::find)
    val operatingState = rememberSaveable(saver = OperatingInputState.Saver) { OperatingInputState() }
    val thermalState = rememberSaveable(saver = ThermalInputState.Saver) { ThermalInputState() }
    var strictSurface by rememberSaveable { mutableStateOf(false) }
    // 「O型環設計」點選的尺寸，帶到「溝槽設計」
    var pendingPick by remember { mutableStateOf<Pair<StandardSize, Gland>?>(null) }
    // 切換分頁時保留各分頁的輸入內容
    val stateHolder = rememberSaveableStateHolder()

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                ChipRow(LengthUnit.entries, unit, { it.label }) { newUnit ->
                    operatingState.changeLengthUnit(unit, newUnit)
                    unit = newUnit
                }
            }
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            stateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> GlandScreen(
                        unit = unit,
                        service = service,
                        onServiceChange = { service = it },
                        material = material,
                        operatingState = operatingState,
                        incoming = pendingPick,
                        onIncomingConsumed = { pendingPick = null },
                        thermalState = thermalState,
                        strictSurface = strictSurface,
                        onStrictSurfaceChange = { strictSurface = it },
                    )
                    1 -> RingDesignScreen(
                        unit = unit,
                        service = service,
                        onServiceChange = { service = it },
                        operatingState = operatingState,
                        onPick = { size, gland -> pendingPick = size to gland; tab = 0 },
                        material = material,
                        thermalState = thermalState,
                        strictSurface = strictSurface,
                        onStrictSurfaceChange = { strictSurface = it },
                    )
                    else -> MaterialScreen(
                        service = service,
                        onServiceChange = { service = it },
                        selected = material,
                        onSelect = { materialCode = it.code; tab = 0 },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OringsAppPreview() {
    MaterialTheme { OringsApp() }
}
