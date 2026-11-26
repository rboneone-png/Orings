package com.orings.app.data

import android.content.Context
import com.orings.core.DesignRecord
import com.orings.core.Hardness
import com.orings.core.HardwareMaterial
import com.orings.core.LengthUnit
import com.orings.core.PressureUnit
import com.orings.core.SealType
import com.orings.core.Service
import org.json.JSONArray
import org.json.JSONObject

/** 設計存檔：以 JSON 存在 App 私有的 SharedPreferences，只在這支手機上。 */
class DesignStore(context: Context) {
    private val prefs = context.getSharedPreferences("designs", Context.MODE_PRIVATE)

    fun load(): List<DesignRecord> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
    }.getOrDefault(emptyList())

    fun save(records: List<DesignRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "records"

        fun toJson(r: DesignRecord) = JSONObject().apply {
            put("name", r.name)
            put("savedAt", r.savedAt)
            put("sealType", r.sealType.name)
            put("service", r.service.name)
            put("unit", r.unit.name)
            put("ringId", r.ringId)
            put("ringCs", r.ringCs)
            put("sizeLabel", r.sizeLabel ?: JSONObject.NULL)
            put("mateDiameter", r.mateDiameter)
            put("grooveDiameter", r.grooveDiameter)
            put("grooveWidth", r.grooveWidth)
            put("faceDepth", r.faceDepth)
            put("materialCode", r.materialCode ?: JSONObject.NULL)
            put("pressureMpa", r.pressureMpa ?: JSONObject.NULL)
            put("pressureUnit", r.pressureUnit.name)
            put("radialGap", r.radialGap)
            put("hardness", r.hardness?.name ?: JSONObject.NULL)
            put("backupRings", r.backupRings ?: JSONObject.NULL)
            put("tMinC", r.tMinC ?: JSONObject.NULL)
            put("tMaxC", r.tMaxC ?: JSONObject.NULL)
            put("hardware", r.hardware.name)
            put("strictSurface", r.strictSurface)
        }

        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
        private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else getDouble(k)

        fun fromJson(o: JSONObject) = DesignRecord(
            name = o.getString("name"),
            savedAt = o.getString("savedAt"),
            sealType = SealType.valueOf(o.getString("sealType")),
            service = Service.valueOf(o.getString("service")),
            unit = LengthUnit.valueOf(o.getString("unit")),
            ringId = o.getDouble("ringId"),
            ringCs = o.getDouble("ringCs"),
            sizeLabel = o.str("sizeLabel"),
            mateDiameter = o.getDouble("mateDiameter"),
            grooveDiameter = o.getDouble("grooveDiameter"),
            grooveWidth = o.getDouble("grooveWidth"),
            faceDepth = o.getDouble("faceDepth"),
            materialCode = o.str("materialCode"),
            pressureMpa = o.dbl("pressureMpa"),
            pressureUnit = PressureUnit.valueOf(o.getString("pressureUnit")),
            radialGap = o.getDouble("radialGap"),
            hardness = o.str("hardness")?.let(Hardness::valueOf),
            backupRings = if (o.isNull("backupRings")) null else o.getInt("backupRings"),
            tMinC = o.dbl("tMinC"),
            tMaxC = o.dbl("tMaxC"),
            hardware = HardwareMaterial.valueOf(o.getString("hardware")),
            strictSurface = o.getBoolean("strictSurface"),
        )
    }
}
