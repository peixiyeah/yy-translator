package com.personal.translator

import org.json.JSONObject

enum class Language(val code: String, val label: String, val english: String) {
    ZH("zh", "中文", "Chinese"), EN("en", "English", "English"),
    JA("ja", "日本語", "Japanese"), KO("ko", "한국어", "Korean"),
    FR("fr", "Français", "French");
}

data class Utterance(
    val id: String, val speaker: Int, val source: Language, val target: Language,
    val original: String, val translated: String, val time: Long = System.currentTimeMillis()
) {
    fun textFor(language: Language) = if (language == source) original else translated
    fun toJson() = JSONObject().put("id", id).put("speaker", speaker).put("source", source.name).put("target", target.name)
        .put("original", original).put("translated", translated).put("time", time)
    companion object {
        fun fromJson(o: JSONObject): Utterance {
            val source = Language.valueOf(o.getString("source"))
            val legacyTarget = when (source) {
                Language.EN -> Language.KO
                Language.KO -> Language.EN
                else -> Language.EN
            }
            return Utterance(o.getString("id"), o.getInt("speaker"), source,
                runCatching { Language.valueOf(o.getString("target")) }.getOrDefault(legacyTarget),
                o.getString("original"), o.getString("translated"), o.getLong("time"))
        }
    }
}
