package com.vlad.gallery.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences("gallery", Context.MODE_PRIVATE)

    fun gridColumns(context: Context): Int = sp(context).getInt("grid_columns", 4)

    fun setGridColumns(context: Context, value: Int) =
        sp(context).edit().putInt("grid_columns", value).apply()

    /** Hidden album paths, e.g. "Pictures/Memes/". Hiding a path hides everything under it. */
    fun hiddenPaths(context: Context): Set<String> =
        sp(context).getStringSet("hidden_paths", emptySet()) ?: emptySet()

    fun setHiddenPaths(context: Context, value: Set<String>) =
        sp(context).edit().putStringSet("hidden_paths", value).apply()
}
