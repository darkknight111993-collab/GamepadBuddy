package com.gamepadbuddy.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lưu/đọc nhiều Profile (theo từng game) dạng JSON trong filesDir (file 06 - Bước 2).
 * Hướng dẫn khuyên Room; ở MVP dùng JSON để build nhanh, dễ nâng lên Room ở Giai đoạn 3.
 */
class ProfileRepository(dir: File) {

    private val file = File(dir, "profiles.json")

    constructor(context: Context) : this(context.filesDir)

    fun getAll(): List<Profile> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { parse(it, arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun getForPackage(pkg: String): Profile? = getAll().firstOrNull { it.packageName == pkg }

    fun save(profile: Profile) {
        val list = getAll().filter { it.id != profile.id }.toMutableList()
        list.add(profile)
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        runCatching { file.writeText(arr.toString()) }
    }

    fun delete(id: String) {
        val list = getAll().filter { it.id != id }
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        runCatching { file.writeText(arr.toString()) }
    }

    private fun parse(id: Int, o: JSONObject): Profile? = runCatching {
        val widgets = mutableListOf<MappedWidget>()
        val arr = o.optJSONArray("widgets") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val w = arr.getJSONObject(i)
            val type = w.getString("type")
            val wid = w.getString("id")
            val x = w.optDouble("x", 0.0).toFloat()
            val y = w.optDouble("y", 0.0).toFloat()
            if (type == "JOYSTICK") {
                widgets += MappedWidget.Joystick(
                    wid, x, y,
                    w.optDouble("radius", 80.0).toFloat(),
                    if (w.optString("axisGroup") == "RIGHT_STICK") AxisGroup.RIGHT_STICK else AxisGroup.LEFT_STICK
                )
            } else {
                widgets += MappedWidget.Button(wid, x, y, w.optInt("boundKeyCode", 0))
            }
        }
        Profile(o.getString("id"), o.optString("packageName", ""), o.optString("name", ""), widgets)
    }.getOrNull()

    private fun toJson(p: Profile): JSONObject = JSONObject().apply {
        put("id", p.id); put("packageName", p.packageName); put("name", p.name)
        val arr = JSONArray()
        p.widgets.forEach { w ->
            val o = JSONObject().apply { put("id", w.id); put("x", w.x); put("y", w.y) }
            when (w) {
                is MappedWidget.Button -> { o.put("type", "BUTTON"); o.put("boundKeyCode", w.boundKeyCode) }
                is MappedWidget.Joystick -> { o.put("type", "JOYSTICK"); o.put("radius", w.radius); o.put("axisGroup", w.axisGroup.name) }
            }
            arr.put(o)
        }
        put("widgets", arr)
    }
}
