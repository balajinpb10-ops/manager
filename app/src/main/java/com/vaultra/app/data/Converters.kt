package com.vaultra.app.data

import androidx.room.TypeConverter

/**
 * Room only stores flat columns, so list-shaped fields are flattened here:
 * attached-image file paths (on CardEntry / DocumentEntry) become a single
 * delimited string, and a TodoEntry's checklist becomes a small JSON array.
 * "|||" is used as the image-path separator since local file paths can never contain it.
 */
class Converters {
    @TypeConverter
    fun fromImageList(images: List<String>): String = images.joinToString("|||")

    @TypeConverter
    fun toImageList(data: String): List<String> =
        if (data.isBlank()) emptyList() else data.split("|||")

    /** Checklist items on a TodoEntry are flattened to a small JSON array, since they're
     *  only ever read/written together with their parent task. */
    @TypeConverter
    fun fromChecklist(items: List<ChecklistItem>): String {
        val arr = org.json.JSONArray()
        items.forEach { item ->
            arr.put(
                org.json.JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("isDone", item.isDone)
                }
            )
        }
        return arr.toString()
    }

    @TypeConverter
    fun toChecklist(data: String): List<ChecklistItem> {
        if (data.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(data)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChecklistItem(id = o.optString("id"), text = o.optString("text"), isDone = o.optBoolean("isDone"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
