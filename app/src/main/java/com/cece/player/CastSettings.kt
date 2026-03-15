package com.cece.player

import android.content.Context

data class CastSettings(val name: String)

fun loadCastSettings(context: Context): CastSettings? {
    return try {
        context.assets.open("cast_settings.txt").bufferedReader().useLines { lines ->
            val map = lines
                .map { it.trim() }
                .filter { it.contains('=') }
                .associate { line ->
                    val idx = line.indexOf('=')
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
            val name = map["name"] ?: return@useLines null
            CastSettings(name)
        }
    } catch (e: Exception) {
        null
    }
}
