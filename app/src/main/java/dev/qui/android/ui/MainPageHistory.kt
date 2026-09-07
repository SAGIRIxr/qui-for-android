package dev.qui.android.ui

/** Visit history is independent of NavController's per-tab saved state and startup page. */
internal fun visitMainPage(history: List<String>, route: String): List<String> =
    if (history.lastOrNull() == route) history else history + route

internal fun previousMainPage(history: List<String>): String? = history.dropLast(1).lastOrNull()

internal const val OPEN_INSTANCE_ID = "open_instance_id"
