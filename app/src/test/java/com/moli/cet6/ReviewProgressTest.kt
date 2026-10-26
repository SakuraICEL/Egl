package com.moli.cet6

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReviewProgressTest {
    private val today = LocalDate.of(2026, 10, 26)

    @Test fun failureResetsAndIsDueToday() {
        assertEquals(ReviewProgress(today, 0, 0), scheduleReview(ReviewProgress(today, 5, 6), 0, today))
    }
    @Test fun vagueResetsStreakAndIsDueTomorrow() {
        assertEquals(ReviewProgress(today.plusDays(1), 2, 0), scheduleReview(ReviewProgress(today, 3, 4), 1, today))
    }
    @Test fun successfulReviewsGrowThenCap() {
        var progress: ReviewProgress? = null
        listOf(1L, 3L, 7L, 14L, 30L, 60L, 60L).forEach { interval ->
            progress = scheduleReview(progress, 2, today)
            assertEquals(today.plusDays(interval), progress!!.due)
        }
        assertEquals(5, progress!!.familiarity)
        assertEquals(6, progress!!.streak)
    }
    @Test fun vagueUnseenWordNeverHasNegativeFamiliarity() {
        assertEquals(0, scheduleReview(null, 1, today).familiarity)
    }
}
