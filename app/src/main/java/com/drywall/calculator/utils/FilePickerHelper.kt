package com.drywall.calculator.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import java.io.InputStreamReader

object FilePickerHelper {
    fun parseJsonZeros(context: Context, uri: Uri): SketchUpData? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = InputStreamReader(stream)
            Gson().fromJson(reader, SketchUpData::class.java)
        }
    }

    data class SketchUpData(val zones: List<ZoneData>)
    data class ZoneData(val name: String, val areaM2: Double)
}
