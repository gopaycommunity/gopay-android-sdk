package cz.gopay.sdk.locales

import android.os.LocaleList
import java.util.IllformedLocaleException
import java.util.Locale

/**
 * The `Accept-Language` value for [languages], the system's languages in its order, built the
 * way the browser SDK's `buildAcceptLanguage` builds it: the first tag as it is, every later
 * one with `;q=` falling by 0.1 per place down to 0.1 (`cs-CZ,cs;q=0.9,en-GB;q=0.8`). Nothing
 * is deduplicated.
 *
 * A tag that is not plain ASCII letters, digits and single hyphens between them is left out, as
 * an HTTP header cannot carry it, and the weights follow the order of what is left; the browser
 * SDK filters nothing, the iOS SDK the same as here. With no tag left, whether [languages] was
 * empty or nothing in it passed, [fallback] stands alone, the device's current language as
 * [languageTag] gives it, as the browser SDK falls back to `navigator.language`; null when even
 * that does not pass, and the request then goes without the header.
 */
internal fun acceptLanguageHeader(
    languages: List<String>,
    fallback: String = languageTag(Locale.getDefault()) ?: ""
): String? {
    val tags = languages.filter { it.matches(LANGUAGE_TAG) }
        .ifEmpty { listOf(fallback).filter { it.matches(LANGUAGE_TAG) } }
        .ifEmpty { return null }
    return tags.mapIndexed { index, tag ->
        // Tenths as integers: the browser SDK's toFixed(1) without the floats.
        if (index == 0) tag else "$tag;q=0.${maxOf(10 - index, 1)}"
    }.joinToString(",")
}

/**
 * [GopayLocales.preferredLanguages] as the platform gives it: `LocaleList.getDefault()`, which
 * on Android 13 and later starts with the languages the user set for the app alone, each as
 * [languageTag]. Empty when the platform cannot say, as in a plain JVM unit test.
 */
internal fun systemLanguageTags(): List<String> = try {
    val list = LocaleList.getDefault()
    (0 until list.size()).mapNotNull { languageTag(list[it]) }
} catch (e: RuntimeException) {
    // Not worth anything a payment needs; the header falls back to the default locale.
    emptyList()
}

/**
 * [locale] as a BCP 47 tag without its extensions: Android 14's regional preferences put
 * `-u-mu-celsius` and the like into the system's locales, which say nothing about the language
 * and which the iOS SDK never sends. Null for a locale the builder refuses.
 */
internal fun languageTag(locale: Locale): String? = try {
    Locale.Builder().setLocale(locale).clearExtensions().build().toLanguageTag()
} catch (e: IllformedLocaleException) {
    null
}

private val LANGUAGE_TAG = Regex("[A-Za-z0-9]+(-[A-Za-z0-9]+)*")
