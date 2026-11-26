package com.orings.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orings.app.data.PdfExporter
import com.orings.core.DesignRecord
import com.orings.core.DesignReport

/** PDF 檔名可用的字元 */
private fun safeFileName(name: String) = name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(60)

/**
 * 回傳一個「匯出 PDF」函式：開啟系統的儲存位置選擇器（可存到「下載」），再寫入報告。
 */
@Composable
fun rememberPdfExport(): (DesignRecord) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<DesignRecord?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val rec = pending
        pending = null
        if (uri == null || rec == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { PdfExporter.write(DesignReport.build(rec), it) }
        }.isSuccess
        Toast.makeText(context, if (ok) "已儲存 PDF" else "PDF 儲存失敗", Toast.LENGTH_SHORT).show()
    }
    return { rec ->
        pending = rec
        launcher.launch("O型環設計_${safeFileName(rec.name)}.pdf")
    }
}

/** 輸入存檔名稱的對話框 */
@Composable
fun SaveDesignDialog(defaultName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("儲存設計") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("設計名稱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { defaultName }) }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 已存的設計：開啟、匯出 PDF、刪除 */
@Composable
fun SavedScreen(
    records: List<DesignRecord>,
    onOpen: (DesignRecord) -> Unit,
    onDelete: (DesignRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exportPdf = rememberPdfExport()
    var confirmDelete by remember { mutableStateOf<DesignRecord?>(null) }

    confirmDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("刪除設計") },
            text = { Text("確定要刪除「${rec.name}」嗎？刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = { onDelete(rec); confirmDelete = null }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("已存的設計（存在這支手機上）", style = MaterialTheme.typography.titleMedium)
        if (records.isEmpty()) {
            Text(
                "還沒有存檔。到「溝槽設計」分頁按「儲存設計」，就會出現在這裡。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        records.forEach { rec ->
            val verdict = runCatching { DesignReport.build(rec).verdict.label }.getOrDefault("無法計算")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rec.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${rec.savedAt}　${rec.sealType.label}・${rec.service.label}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        (rec.sizeLabel ?: "自訂尺寸") + "　ID ${rec.unit.format(rec.ringId)} × CS ${rec.unit.format(rec.ringCs)} ${rec.unit.symbol}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(verdict, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onOpen(rec) }) { Text("開啟") }
                        OutlinedButton(onClick = { exportPdf(rec) }) { Text("下載 PDF") }
                        TextButton(onClick = { confirmDelete = rec }) {
                            Text("刪除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
