package com.moli.cet6

import java.time.LocalDate

data class ReviewProgress(val due: LocalDate, val familiarity: Int, val streak: Int)

fun scheduleReview(previous: ReviewProgress?, level: Int, today: LocalDate): ReviewProgress {
    require(level in 0..2)
    val old = previous ?: ReviewProgress(today, 0, 0)
    return when (level) {
        0 -> ReviewProgress(today, 0, 0)
        1 -> ReviewProgress(today.plusDays(1), (old.familiarity - 1).coerceAtLeast(0), 0)
        else -> {
            val streak = (old.streak + 1).coerceAtMost(6)
            val intervals = listOf(1L, 3L, 7L, 14L, 30L, 60L)
            ReviewProgress(today.plusDays(intervals[streak - 1]), (old.familiarity + 1).coerceAtMost(5), streak)
        }
    }
}
