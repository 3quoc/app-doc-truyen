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
        val list = mutableListOf<HistoryItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HistoryItem(
                        storyId = obj.getString("storyId"),
                        storyName = obj.getString("storyName"),
                        chapterName = obj.getString("chapterName"),
                        url = obj.getString("url"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveHistoryItem(storyId: String, storyName: String, chapterName: String, url: String) {
        val currentList = getHistory().toMutableList()
        // Remove existing item for this story
        currentList.removeAll { it.storyId == storyId }
        
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
