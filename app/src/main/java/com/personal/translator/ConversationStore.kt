package com.personal.translator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ConversationStore(context: Context) {
    private val file = File(context.filesDir, "conversations.json")
    private var data = if (file.exists()) JSONObject(file.readText()) else JSONObject().put("current", JSONArray()).put("archives", JSONArray())
    fun current() = decode(data.getJSONArray("current"))
    fun save(messages: List<Utterance>) {
        val next = JSONObject(data.toString()).put("current", encode(messages))
        write(next)
    }
    fun archive(messages: List<Utterance>) {
        val next = JSONObject(data.toString())
        if (messages.isNotEmpty()) {
            val all = next.getJSONArray("archives")
            all.put(JSONObject().put("time", System.currentTimeMillis()).put("messages", encode(messages)))
            if (all.length() > 50) all.remove(0)
        }
        write(next.put("current", JSONArray()))
    }
    fun resume(archiveTime: Long, current: List<Utterance>): List<Utterance> {
        val next = JSONObject(data.toString())
        val all = next.getJSONArray("archives")
        val index = (0 until all.length()).firstOrNull { all.getJSONObject(it).optLong("time") == archiveTime }
            ?: error("找不到这段历史对话，请返回刷新列表。")
        val restored = decode(all.getJSONObject(index).getJSONArray("messages"))
        all.remove(index)
        if (current.isNotEmpty()) {
            all.put(JSONObject().put("time", System.currentTimeMillis()).put("messages", encode(current)))
            if (all.length() > 50) all.remove(0)
        }
        write(next.put("current", encode(restored)))
        return restored
    }
    fun archives(): List<Pair<Long, List<Utterance>>> {
        val all = data.getJSONArray("archives")
        return (0 until all.length()).map { i -> all.getJSONObject(i).let { it.getLong("time") to decode(it.getJSONArray("messages")) } }.reversed()
    }
    private fun write(next: JSONObject) {
        val tmp = File(file.parentFile, "conversations.tmp")
        tmp.writeText(next.toString())
        check(tmp.renameTo(file)) { "无法保存对话记录。" }
        data = next
    }
    private fun encode(messages: List<Utterance>) = JSONArray().apply { messages.forEach { put(it.toJson()) } }
    private fun decode(array: JSONArray) = (0 until array.length()).map { Utterance.fromJson(array.getJSONObject(it)) }
}
