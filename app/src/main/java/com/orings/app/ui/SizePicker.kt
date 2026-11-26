package com.orings.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orings.core.SizeCatalog
import com.orings.core.Standard
import com.orings.core.StandardSize

/** 從標準尺寸表挑選 O 型環 */
@Composable
fun SizePickerDialog(
    onDismiss: () -> Unit,
    onPick: (StandardSize) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var standards by remember { mutableStateOf(Standard.entries.toSet()) }
    val results = remember(query, standards) { SizeCatalog.search(query, standards) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("標準尺寸表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("代號或內徑（例如 210、P22、35）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Standard.entries.forEach { s ->
                        FilterChip(
                            selected = s in standards,
                            onClick = { standards = if (s in standards) standards - s else standards + s },
                            label = { Text(s.label) },
                        )
                    }
                }
                Text("共 ${results.size} 筆", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(results, key = { it.standard.name + it.code }) { size ->
                        SizeRow(size, Modifier.clickable { onPick(size) })
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}

@Composable
private fun SizeRow(size: StandardSize, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(size.displayName, fontWeight = FontWeight.Medium)
        Text("ID %.2f × CS %.2f".format(size.innerDiameter, size.crossSection))
    }
}

/** 「找適用尺寸」的推薦清單 */
@Composable
fun CandidateList(
    candidates: List<SizeCatalog.Candidate>,
    onPick: (SizeCatalog.Candidate) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("適用的標準尺寸（點選帶入）", fontWeight = FontWeight.Bold)
            if (candidates.isEmpty()) {
                Text("找不到合適尺寸，請確認配合直徑。")
            }
            candidates.forEach { c ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(c) }
                        .padding(vertical = 6.dp),
                ) {
                    SizeRow(c.size)
                    Text(
                        "溝槽 Ø%.2f × 寬 %.2f｜壓縮 %.1f%%  拉伸 %.1f%%  填充 %.1f%%".format(
                            c.gland.grooveDiameter,
                            c.gland.grooveWidth,
                            c.result.compression * 100,
                            c.result.stretch * 100,
                            c.result.fill * 100,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
