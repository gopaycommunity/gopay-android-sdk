package cz.gopay.sdk.telemetry

import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * The gw-logger event schema (OpenAPI 0.9.0), as far as the SDK's events can break it: every type
 * has `additionalProperties: false`, so one key too many drops the whole event, and each type
 * has its own required keys and caps. A test that sends an event checks it against this.
 */
internal object GwLoggerSchema {

    private sealed class Field {
        data class Str(val min: Int = 0, val max: Int, val nullable: Boolean = false, val enum: Set<String>? = null) : Field()
        data class Num(val nullable: Boolean = false, val min: Long? = null) : Field()
    }

    private val common = mapOf(
        "payment_session_id" to Field.Str(1, 256),
        "shareable_key" to Field.Str(1, 256),
        "client_id" to Field.Str(1, 256),
        "sdk_instance_id" to Field.Str(1, 256),
        "seq" to Field.Num(min = 1),
        "sdk_version" to Field.Str(1, 64),
        "integration" to Field.Str(1, 64),
        "payment_method" to Field.Str(1, 64),
        "flow" to Field.Str(1, 64),
        "error_code" to Field.Str(1, 128),
        "trace_id" to Field.Str(max = 256),
        "transaction_id" to Field.Str(max = 256),
        "origin" to Field.Str(max = 2048),
        "duration" to Field.Num(nullable = true)
    )

    private val required = listOf("event_type", "trace_id", "origin", "duration")

    private val types: Map<String, Pair<Map<String, Field>, List<String>>> = mapOf(
        "navigation" to (
            mapOf(
                "navigation_type" to Field.Str(max = 64, enum = setOf("init", "ready", "navigate", "leave")),
                "target" to Field.Str(max = 2048, nullable = true)
            ) to listOf("navigation_type", "target")
            ),
        "interaction" to (
            mapOf(
                "interaction_type" to Field.Str(max = 64, enum = setOf("click", "submit")),
                "element_id" to Field.Str(max = 256),
                "error_message" to Field.Str(max = 256, nullable = true)
            ) to listOf("interaction_type", "element_id")
            ),
        "api_call" to (
            mapOf(
                "action" to Field.Str(max = 256),
                "http_method" to Field.Str(1, 64),
                "target" to Field.Str(max = 2048),
                "status_code" to Field.Num(nullable = true),
                "req_body" to Field.Str(max = 4096, nullable = true),
                "res_body" to Field.Str(max = 4096, nullable = true)
            ) to listOf("target", "status_code")
            ),
        "js_event" to (
            mapOf(
                "function_name" to Field.Str(max = 256),
                "params" to Field.Str(max = 4096, nullable = true),
                "return_value" to Field.Str(max = 4096, nullable = true)
            ) to listOf("function_name")
            )
    )

    fun assertValid(event: Map<String, Any?>) {
        val type = event["event_type"] as? String ?: return fail("no event_type in $event")
        val (own, ownRequired) = types[type] ?: return fail("unknown event_type $type")
        val fields = common + own
        for (key in required + ownRequired) {
            assertTrue("$type lacks required $key: $event", event.containsKey(key))
        }
        assertTrue(
            "$type has neither payment_session_id nor client_id: $event",
            event.containsKey("payment_session_id") || event.containsKey("client_id")
        )
        for ((key, value) in event) {
            if (key == "event_type") continue
            val field = fields[key] ?: return fail("$type does not allow $key: $event")
            check(type, key, field, value)
        }
    }

    private fun check(type: String, key: String, field: Field, value: Any?) {
        when (field) {
            is Field.Str -> {
                if (value == null) {
                    assertTrue("$type.$key is null", field.nullable)
                    return
                }
                assertTrue("$type.$key is not a string: $value", value is String)
                val text = value as String
                assertTrue("$type.$key is shorter than ${field.min}", text.length >= field.min)
                assertTrue("$type.$key is longer than ${field.max}", text.length <= field.max)
                field.enum?.let { assertTrue("$type.$key=$text is not in $it", text in it) }
            }
            is Field.Num -> {
                if (value == null) {
                    assertTrue("$type.$key is null", field.nullable)
                    return
                }
                val literal = (value as? JsonText.JsonNumber)?.literal
                    ?: return fail("$type.$key is not a number: $value")
                val number = literal.toLongOrNull() ?: return fail("$type.$key is not an integer: $literal")
                field.min?.let { assertTrue("$type.$key=$number is below $it", number >= it) }
            }
        }
    }
}
