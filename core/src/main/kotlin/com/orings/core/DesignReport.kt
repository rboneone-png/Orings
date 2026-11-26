package com.orings.core

import java.util.Locale

/**
 * 一筆溝槽設計的完整輸入，用於存檔與產生報告。長度一律 mm。
 *
 * @param pressureMpa null = 未輸入壓力
 * @param hardness null = 自動（依推薦）
 * @param backupRings null = 自動（依推薦）
 * @param tMinC 與 [tMaxC] 都是 null = 不做熱膨脹修正
 */
data class DesignRecord(
    val name: String,
    /** 存檔時間（顯示用文字，由呼叫端提供，例如 2026-11-26 16:30） */
    val savedAt: String,
    val sealType: SealType,
    val service: Service,
    val unit: LengthUnit,
    val ringId: Double,
    val ringCs: Double,
    val sizeLabel: String?,
    val mateDiameter: Double,
    val grooveDiameter: Double,
    val grooveWidth: Double,
    val faceDepth: Double,
    val materialCode: String?,
    val pressureMpa: Double?,
    val pressureUnit: PressureUnit,
    val radialGap: Double,
    val hardness: Hardness?,
    val backupRings: Int?,
    val tMinC: Double?,
    val tMaxC: Double?,
    val hardware: HardwareMaterial,
    val strictSurface: Boolean,
) {
    val ring: ORing get() = ORing(ringId, ringCs)

    val gland: Gland
        get() = when (sealType) {
            SealType.FACE -> Gland(sealType, grooveWidth, grooveDiameter, faceDepth = faceDepth)
            else -> Gland(sealType, grooveWidth, grooveDiameter, mateDiameter)
        }

    val material: Material? get() = materialCode?.let(Materials::find)

    /** 依輸入與推薦組出實際採用的使用條件 */
    fun operating(): Operating? {
        val p = pressureMpa ?: return null
        val rec = Extrusion.recommend(p, radialGap)
        return Operating(
            pressureMpa = p,
            radialGap = radialGap,
            hardness = hardness ?: rec.hardness ?: Hardness.A90,
            backupRings = backupRings ?: if (rec.needsBackup) 1 else 0,
        )
    }

    fun thermal(): ThermalInput? =
        if (tMinC == null || tMaxC == null) null else ThermalInput.of(tMinC, tMaxC, material, hardware)
}

/**
 * 設計報告：由 [DesignRecord] 重新計算，產生可直接排版的文字內容。
 * App（PDF）與網頁版共用相同的內容與文字。
 */
object DesignReport {

    data class Section(
        val title: String,
        val rows: List<Pair<String, String>>,
        val notes: List<String> = emptyList(),
    )

    enum class Verdict(val label: String) {
        OK("✓ 設計合格"),
        WARNING("! 可用，但有警告"),
        ERROR("✗ 設計不合格"),
    }

    data class Report(
        val title: String,
        val subtitle: String,
        val verdict: Verdict,
        val sections: List<Section>,
    )

    private val hardwareNoMaterialNote = "未指定（以 NBR 計算膨脹）"

    fun build(rec: DesignRecord): Report {
        val u = rec.unit
        fun len(mm: Double) = "${u.format(mm)} ${u.symbol}"
        fun pct(v: Double) = String.format(Locale.US, "%.1f%%", v * 100)
        fun issueLine(i: Issue) = (if (i.severity == Severity.ERROR) "✗ " else "! ") + i.message

        val ring = rec.ring
        val gland = rec.gland
        val op = rec.operating()
        val result = GlandCalculator.calculate(ring, gland, rec.service, operating = op)
        val effectiveMaterial = rec.material ?: Materials.find("NBR")
        val thermal = rec.thermal()?.let { Thermal.analyze(ring, gland, it, rec.service, op, effectiveMaterial) }

        val sections = mutableListOf<Section>()

        sections += Section(
            "基本資料",
            listOf(
                "設計名稱" to rec.name,
                "存檔時間" to rec.savedAt,
                "密封型式" to rec.sealType.label,
                "使用條件" to rec.service.label,
                "長度單位" to u.label,
            ),
        )

        sections += Section(
            "O 型環",
            listOf(
                "規格" to (rec.sizeLabel ?: "自訂尺寸"),
                "內徑 ID" to len(ring.innerDiameter),
                "線徑 CS" to len(ring.crossSection),
                "外徑 OD" to len(ring.outerDiameter),
                "材質" to (rec.material?.label ?: hardwareNoMaterialNote),
            ),
        )

        val (mateLabel, grooveLabel) = when (rec.sealType) {
            SealType.PISTON -> "缸筒內徑" to "溝槽底徑（活塞上）"
            SealType.ROD -> "軸桿外徑" to "溝槽底徑（殼體內）"
            SealType.FACE -> "溝槽深度" to "溝槽內徑"
        }
        sections += Section(
            "溝槽",
            buildList {
                if (rec.sealType == SealType.FACE) {
                    add(grooveLabel to len(gland.grooveDiameter))
                    add(mateLabel to len(gland.faceDepth))
                } else {
                    add(mateLabel to len(gland.mateDiameter))
                    add(grooveLabel to len(gland.grooveDiameter))
                }
                add("溝槽寬度" to len(gland.grooveWidth))
                add("溝槽深度（計算）" to len(result.depth))
            },
        )

        val l = result.limits
        sections += Section(
            "計算結果（組裝溫度）",
            buildList {
                add("壓縮率" to "${pct(result.compression)}（建議 ${pct(l.minCompression)}–${pct(l.maxCompression)}）")
                add("拉伸率" to "${pct(result.stretch)}（建議 ≤ ${pct(l.maxStretch)}）")
                add("填充率" to "${pct(result.fill)}（建議 ≤ ${pct(l.maxFill)}）")
                add("拉伸後有效線徑" to len(result.effectiveCrossSection))
                result.extrusionLimit?.let { add("擠出極限" to String.format(Locale.US, "%.1f MPa", it)) }
                if (op != null && op.backupRings > 0) add("扣擋圈後溝槽寬" to len(result.effectiveWidth))
            },
            result.issues.map(::issueLine).ifEmpty { listOf("無問題") },
        )

        if (op != null && rec.pressureMpa != null) {
            val rec2 = Extrusion.recommend(rec.pressureMpa, rec.radialGap)
            val pu = rec.pressureUnit
            sections += Section(
                "壓力與硬度",
                listOf(
                    "工作壓力" to String.format(Locale.US, "%.${pu.decimals}f %s", pu.fromMpa(rec.pressureMpa), pu.label)
                        .let { s -> if (pu != PressureUnit.MPA) s + String.format(Locale.US, "（%.2f MPa）", rec.pressureMpa) else s },
                    "擠出間隙（單邊）" to len(rec.radialGap),
                    "建議硬度" to ((rec2.hardness?.let { "Shore ${it.label}" } ?: "不適用 O 型環") +
                        if (rec2.needsBackup && rec2.hardness != null) " + 擋圈" else ""),
                    "採用硬度" to "Shore ${op.hardness.label}" + if (rec.hardness == null) "（自動）" else "",
                    "擋圈" to listOf("不用", "單側", "雙側")[op.backupRings] + if (rec.backupRings == null) "（自動）" else "",
                ),
                listOf(rec2.reason),
            )
        }

        if (thermal != null) {
            val input = rec.thermal()!!
            sections += Section(
                "熱膨脹修正",
                buildList {
                    add("金屬件材質" to rec.hardware.label)
                    add(
                        "膨脹係數" to String.format(
                            Locale.US, "O 型環 %.1f×10⁻⁴/°C，金屬 %.1f×10⁻⁶/°C",
                            input.ringCte * 1e4, input.hardwareCte * 1e6,
                        ),
                    )
                    thermal.points.forEach { p ->
                        add(
                            "${p.label} ${fmtTemp(p.tempC)}°C" to
                                "壓縮 ${pct(p.result.compression)}／拉伸 ${pct(p.result.stretch)}／填充 ${pct(p.result.fill)}",
                        )
                    }
                },
                (thermal.points.drop(1).flatMap { p -> p.result.issues.map { "${p.label} ${fmtTemp(p.tempC)}°C：" + issueLine(it) } } +
                    thermal.notes.map { "! $it" }).ifEmpty { listOf("整個溫度範圍無問題") },
            )
        }

        val rough = SurfaceFinish.recommend(rec.service, rec.strictSurface)
        sections += Section(
            "表面粗糙度建議（${rec.service.label}${if (rec.strictSurface) "，壓力脈動／氣體或真空" else ""}）",
            rough.map { it.surface.label to roughnessText(it, u) },
            rough.map { "${it.surface.label.substringBefore("（")}：${it.note}" },
        )

        sections += Section(
            "注意事項",
            emptyList(),
            listOf(
                "建議範圍、擠出極限、膨脹係數與表面粗糙度皆為一般工業經驗值，正式設計請以材質與密封件廠商型錄為準。",
                "標準尺寸依 SAE AS568 與 JIS B2401 規則整理，使用前請與標準原文核對。",
            ),
        )

        val anyError = !result.isOk || (thermal != null && !thermal.isOk)
        val anyWarning = result.issues.isNotEmpty() ||
            (thermal != null && (thermal.notes.isNotEmpty() || thermal.points.any { it.result.issues.isNotEmpty() }))
        val verdict = when {
            anyError -> Verdict.ERROR
            anyWarning -> Verdict.WARNING
            else -> Verdict.OK
        }

        return Report(
            title = "O 型環溝槽設計報告",
            subtitle = "${rec.name}　${rec.savedAt}",
            verdict = verdict,
            sections = sections,
        )
    }

    /** 粗糙度文字：公制 µm（一位小數），英制 µin（整數） */
    fun roughnessText(r: Roughness, unit: LengthUnit): String {
        val sym = if (unit == LengthUnit.MM) "µm" else "µin"
        fun v(um: Double) = if (unit == LengthUnit.MM) String.format(Locale.US, "%.1f", um)
        else String.format(Locale.US, "%.0f", um * SurfaceFinish.UIN_PER_UM)
        val ra = r.raMin?.let { "Ra ${v(it)}–${v(r.raMax)}" } ?: "Ra ≤ ${v(r.raMax)}"
        return "$ra $sym（Rz ≤ ${v(r.rzMax)} $sym）"
    }

    /** 純文字版報告（測試與分享用） */
    fun toText(report: Report): String = buildString {
        appendLine(report.title)
        appendLine(report.subtitle)
        appendLine(report.verdict.label)
        report.sections.forEach { s ->
            appendLine()
            appendLine("【${s.title}】")
            s.rows.forEach { (k, v) -> appendLine("$k：$v") }
            s.notes.forEach { appendLine("・$it") }
        }
    }

    private fun fmtTemp(v: Double) = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
}
