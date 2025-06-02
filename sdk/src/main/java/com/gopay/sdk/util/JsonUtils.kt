package com.gopay.sdk.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Utility class for JSON serialization and deserialization using Moshi
 */
object JsonUtils {
    
    /**
     * Shared Moshi instance with Kotlin support
     */
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    /**
     * Converts an object to JSON string
     * 
     * @param value The object to serialize (can be null)
     * @param clazz The class type of the object
     * @return JSON string representation of the object, or null if serialization fails or value is null
     */
    fun <T> toJson(value: T?, clazz: Class<T>): String? {
        return try {
            if (value == null) return null
            val adapter = moshi.adapter(clazz)
            adapter.toJson(value)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Converts a JSON string to an object
     * 
     * @param json The JSON string to deserialize
     * @param clazz The class type to deserialize to
     * @return The deserialized object, or null if deserialization fails
     */
    fun <T> fromJson(json: String, clazz: Class<T>): T? {
        return try {
            val adapter = moshi.adapter(clazz)
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Inline reified version of toJson for easier usage
     */
    inline fun <reified T> toJson(value: T?): String? {
        return toJson(value, T::class.java)
    }
    
    /**
     * Inline reified version of fromJson for easier usage
     */
    inline fun <reified T> fromJson(json: String): T? {
        return fromJson(json, T::class.java)
    }
} 