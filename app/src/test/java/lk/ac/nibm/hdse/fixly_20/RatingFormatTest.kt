package lk.ac.nibm.hdse.fixly_20

import lk.ac.nibm.hdse.fixly_20.ui.components.formatRating
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RatingFormatTest {
    @Test
    fun emptyRatingsArePresentedAsNewProvider() {
        assertEquals("New provider · no reviews yet", formatRating(0.0, 0))
        assertEquals("New provider · no reviews yet", formatRating(4.8, -1))
    }

    @Test
    fun ratingUsesOneDecimalPlaceAndPluralizesReviewCount() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("★ 4.5 (1 review)", formatRating(4.5, 1))
            assertEquals("★ 4.8 (12 reviews)", formatRating(4.75, 12))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
