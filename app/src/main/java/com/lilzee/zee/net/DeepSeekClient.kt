package com.lilzee.zee.net

import com.lilzee.zee.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class LookupResult(
    val word: String,
    val phonetic: String?,
    val meaning: String,
    val exampleEn: String?,
    val exampleZh: String?,
)

object DeepSeekClient {

    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    private const val MODEL = "deepseek-chat"

    // Key 由构建脚本从本机 local.properties 或环境变量注入（不入库），见 README「配置 API Key」。
    private val API_KEY: String get() = BuildConfig.DEEPSEEK_API_KEY

    private const val SYSTEM_WORD =
        """你是英语词典助手。用户给你一个英文单词。只输出一个JSON对象，不要输出任何其他文字，字段：
word: 原单词
phonetic: 音标（如 /rɪˈzɪliənt/）
meaning: 最常用词性+中文释义（多义词最多给2个）
example_en: 一个自然地道的英文例句
example_zh: 例句的中文翻译"""

    private const val SYSTEM_SENTENCE =
        """你是英语词典助手。用户给出一个英文句子，以及句中他想学习的单词。只输出一个JSON对象，不要输出任何其他文字，字段：
word: 该单词（保持用户给出的拼写）
phonetic: 音标
meaning: 该单词在这个句子语境下的词性+中文释义
example_en: 原样返回用户给出的整个句子
example_zh: 整个句子的中文翻译"""

    private const val SYSTEM_TRANSLATE =
        "你是翻译助手。将用户提供的英文翻译成流畅自然的中文，只输出译文本身，不要解释、不要重复原文。"

    /** 单词模式：AI 生成例句 */
    suspend fun lookupWord(word: String): LookupResult =
        request(SYSTEM_WORD, "单词：$word")

    /** 句子模式：原句作为例句，释义结合语境 */
    suspend fun lookupInSentence(word: String, sentence: String): LookupResult =
        request(SYSTEM_SENTENCE, "句子：$sentence\n单词：$word")

    /** 整句翻译：进弹窗立刻调用，读懂第一优先 */
    suspend fun translateSentence(sentence: String): String {
        val body = JSONObject().apply {
            put("model", MODEL)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_TRANSLATE))
                    put(JSONObject().put("role", "user").put("content", sentence))
                }
            )
            put("temperature", 0.2)
            put("max_tokens", 500)
        }
        return post(body)
    }

    private suspend fun request(system: String, user: String): LookupResult {
        val body = JSONObject().apply {
            put("model", MODEL)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user))
                }
            )
            put("response_format", JSONObject().put("type", "json_object"))
            put("temperature", 0.3)
            put("max_tokens", 400)
        }
        return parseResult(post(body))
    }

    private suspend fun post(body: JSONObject): String {
        if (API_KEY.isBlank()) {
            throw IOException("尚未配置 DeepSeek API Key，见项目 README 的「配置 API Key」")
        }
        return withContext(Dispatchers.IO) {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $API_KEY")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("DeepSeek HTTP $code: ${text.take(200)}")

            JSONObject(text)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        } finally {
            conn.disconnect()
        }
        }
    }

    private fun parseResult(content: String): LookupResult {
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val obj = JSONObject(cleaned)
        return LookupResult(
            word = obj.optString("word"),
            phonetic = obj.optString("phonetic").ifBlank { null },
            meaning = obj.optString("meaning").ifBlank { "（无释义）" },
            exampleEn = obj.optString("example_en").ifBlank { null },
            exampleZh = obj.optString("example_zh").ifBlank { null },
        )
    }
}
