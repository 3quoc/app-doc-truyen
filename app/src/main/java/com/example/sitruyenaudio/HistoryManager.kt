package com.example.sitruyenaudio

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val storyId: String,
    val storyName: String,
    val chapterName: String,
    val url: String,
    val timestamp: Long
)

class HistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ReadingHistory", Context.MODE_PRIVATE)

    fun getHistory(): List<HistoryItem> {
        val jsonStr = prefs.getString("history_list", "[]") ?: "[]"
        var modified = false
        val list = mutableListOf<HistoryItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawStoryName = obj.getString("storyName")
                val cleanedStoryName = rawStoryName
                    .replace(Regex("\\s*(?:\\|| - |- )\\s*Si Truyện CV$", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s*(?:\\|| - |- )\\s*SiTruyenCV$", RegexOption.IGNORE_CASE), "")
                if (cleanedStoryName != rawStoryName) {
                    modified = true
                }
                list.add(
                    HistoryItem(
                        storyId = obj.getString("storyId"),
                        storyName = cleanedStoryName,
                        chapterName = obj.getString("chapterName"),
                        url = obj.getString("url"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sanitizedList = list.filter { item ->
            val nameLower = item.storyName.lowercase()
            val chapterLower = item.chapterName.lowercase()
            val isInvalid = nameLower.isEmpty() ||
                    nameLower.contains("không tìm thấy trang") ||
                    nameLower.contains("trang không tồn tại") ||
                    nameLower.contains("loading") ||
                    nameLower == "si truyện cv" ||
                    nameLower == "truyentv" ||
                    nameLower == "truyện cv" ||
                    nameLower == "truyện chữ" ||
                    chapterLower.contains("không tìm thấy trang") ||
                    chapterLower.contains("trang không tồn tại")
            !isInvalid
        }

        if (sanitizedList.size != list.size || modified) {
            saveHistoryList(sanitizedList)
        }

        return sanitizedList.sortedByDescending { it.timestamp }
    }

    private fun saveHistoryList(list: List<HistoryItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("storyId", item.storyId)
            obj.put("storyName", item.storyName)
            obj.put("chapterName", item.chapterName)
            obj.put("url", item.url)
            obj.put("timestamp", item.timestamp)
            array.put(obj)
        }
        prefs.edit().putString("history_list", array.toString()).apply()
    }

    fun getSavedChapterUrl(storyId: String, storyName: String): String? {
        val currentList = getHistory()
        val match = currentList.find { 
            (storyId.isNotEmpty() && it.storyId == storyId) || 
            (storyName.isNotEmpty() && it.storyName.equals(storyName, ignoreCase = true)) 
        }
        return match?.url
    }

    fun saveHistoryItem(storyId: String, storyName: String, chapterName: String, url: String) {
        val currentList = getHistory().toMutableList()
        // Remove existing item for this story by ID or Name
        currentList.removeAll { 
            (storyId.isNotEmpty() && it.storyId == storyId) || 
            (storyName.isNotEmpty() && it.storyName.equals(storyName, ignoreCase = true)) 
        }
        
        // Add new item
        currentList.add(HistoryItem(storyId, storyName, chapterName, url, System.currentTimeMillis()))
        
        // Convert to JSON
        val array = JSONArray()
        for (item in currentList) {
            val obj = JSONObject()
            obj.put("storyId", item.storyId)
            obj.put("storyName", item.storyName)
            obj.put("chapterName", item.chapterName)
            obj.put("url", item.url)
            obj.put("timestamp", item.timestamp)
            array.put(obj)
        }
        
        prefs.edit().putString("history_list", array.toString()).apply()
    }
}
