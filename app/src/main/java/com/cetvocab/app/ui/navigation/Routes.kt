package com.cetvocab.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination of the single-activity app.
 *
 * Routes are plain strings with typed builders so that no screen has to know how another
 * screen encodes its arguments, and no screen ever receives the NavController itself.
 */
object Routes {
    const val ONBOARDING = "onboarding"

    // Bottom-navigation destinations.
    const val HOME = "home"
    const val REVIEW = "review"
    const val EXAM_SETUP = "exam-setup"
    const val STATS = "stats"
    const val PROFILE = "profile"

    // Full-screen flows.
    const val STUDY_SESSION = "study/session?drillIds={drillIds}"
    const val WORD_DETAIL = "word/{wordId}"
    const val EXAM_RUN = "exam/run/{examId}"
    const val EXAM_RESULT = "exam/result/{examId}"
    const val SETTINGS = "settings"
    const val DATA_TRANSFER = "data-transfer"
    const val WORD_BROWSER = "word-browser"
    const val ABOUT = "about"
    const val LICENSES = "licenses"
    const val PACKS = "packs"

    const val ARG_DRILL_IDS = "drillIds"
    const val ARG_WORD_ID = "wordId"
    const val ARG_EXAM_ID = "examId"

    /** Study session route; [drillIds] empty means "today's normal queue". */
    fun studySession(drillIds: List<Long> = emptyList()): String =
        if (drillIds.isEmpty()) "study/session?drillIds=" else "study/session?drillIds=${drillIds.joinToString(",")}"

    fun wordDetail(wordId: Long): String = "word/$wordId"

    fun examRun(examId: Long): String = "exam/run/$examId"

    fun examResult(examId: Long): String = "exam/result/$examId"
}

/** The five tabs of the bottom bar. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "首页", Icons.Filled.Home),
    REVIEW(Routes.REVIEW, "错题本", Icons.Filled.Warning),
    EXAM(Routes.EXAM_SETUP, "考试", Icons.Filled.Edit),
    STATS(Routes.STATS, "统计", Icons.Filled.DateRange),
    PROFILE(Routes.PROFILE, "我的", Icons.Filled.Person),
    ;

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}
