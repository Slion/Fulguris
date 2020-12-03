package acr.browser.lightning

class Entitlement {
    companion object {
        // @JvmStatic is there to avoid having to use the companion object when calling that function
        // See: https://www.baeldung.com/kotlin-static-methods
        @JvmStatic
        fun maxTabCount(aSponsorship: Sponsorship): Int {
            val kMaxTabCount = 10000;
            return when (aSponsorship) {
                Sponsorship.TIN -> 20
                Sponsorship.BRONZE -> kMaxTabCount
                Sponsorship.SILVER -> kMaxTabCount
                Sponsorship.GOLD -> kMaxTabCount
                Sponsorship.PLATINUM -> kMaxTabCount
                Sponsorship.DIAMOND -> kMaxTabCount
                // Defensive
                else -> kMaxTabCount
            }
        }
    }
}

