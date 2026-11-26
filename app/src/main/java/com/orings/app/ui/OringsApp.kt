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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.orings.core.LengthUnit
import com.orings.core.Materials
import com.orings.core.Service

/** App 主框架：分頁、共用狀態（單位、使用條件、選用材質） */
@Composable
fun OringsApp() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var unit by rememberSaveable { mutableStateOf(LengthUnit.MM) }
    var service by rememberSaveable { mutableStateOf(Service.STATIC) }
    var materialCode by rememberSaveable { mutableStateOf<String?>(null) }
    val material = materialCode?.let(Materials::find)
    // 切換分頁時保留各分頁的輸入內容
    val stateHolder = rememberSaveableStateHolder()

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("溝槽設計") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("材質選擇") })
            }
            stateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> GlandScreen(
                        unit = unit,
                        onUnitChange = { unit = it },
                        service = service,
                        onServiceChange = { service = it },
                        material = material,
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
