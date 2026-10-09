package com.woojik.aircallai.ai.cloud

import org.json.JSONArray
import org.json.JSONObject

/** Provider-native calls use the same app validation and one-time approval as legacy calls. */
internal object NativeToolCalls {
    const val MARKER = "TOOL_JSON:"
    private val definitions = listOf(
        Triple("tools__list", emptyList<String>(), emptyList<String>()),
        Triple("github__read_repository", listOf("owner", "repo"), emptyList()),
        Triple("github__read_file", listOf("owner", "repo", "path"), emptyList()),
        Triple("github__create_issue", listOf("owner", "repo", "title"), listOf("body")),
        Triple("github__create_pull_request", listOf("owner", "repo", "title", "head", "base"), listOf("body")),
        Triple("notes__add_note", listOf("text"), emptyList()),
        Triple("notes__search_notes", listOf("query"), emptyList()),
        Triple("notes__list_notes", emptyList(), listOf("limit")),
        Triple("gmail__send_email", listOf("to", "subject", "body"), emptyList()),
    )
    fun schema(): JSONArray = JSONArray().also { tools ->
        definitions.forEach { (name, required, optional) ->
            val properties = JSONObject()
            (required + optional).forEach { properties.put(it, JSONObject().put("type", "string")) }
            tools.put(JSONObject().put("type", "function").put("function", JSONObject()
                .put("name", name).put("description", name.replace("__", ".") + "; app approval is required for writes")
                .put("parameters", JSONObject().put("type", "object").put("properties", properties)
                    .put("required", JSONArray(required)).put("additionalProperties", false))))
        }
    }
    fun envelope(id: String, name: String, arguments: String): String {
        require(definitions.any { it.first == name } && id.isNotBlank())
        val parts = name.split("__")
        val args = JSONObject(arguments)
        require(args.keys().asSequence().all { args.get(it) is String })
        return MARKER + JSONObject().put("id", id).put("tool", parts[0]).put("action", parts[1]).put("arguments", args)
    }
    fun decode(text: String): JSONObject? = if (!text.startsWith(MARKER)) null else runCatching {
        val tokenizer = org.json.JSONTokener(text.removePrefix(MARKER))
        (tokenizer.nextValue() as JSONObject).also {
            require(tokenizer.nextClean() == '\u0000')
            require(it.keys().asSequence().toSet() == setOf("id", "tool", "action", "arguments"))
            require(definitions.any { definition -> definition.first == it.getString("tool") + "__" + it.getString("action") })
            val args = it.getJSONObject("arguments")
            require(args.keys().asSequence().all { key -> args.get(key) is String })
            require(it.getString("id").isNotBlank())
        }
    }.getOrNull()
    fun message(envelope: JSONObject): JSONObject = JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
        .put("tool_calls", JSONArray().put(JSONObject().put("id", envelope.getString("id")).put("type", "function")
            .put("function", JSONObject().put("name", envelope.getString("tool") + "__" + envelope.getString("action"))
                .put("arguments", envelope.getJSONObject("arguments").toString()))))
}
