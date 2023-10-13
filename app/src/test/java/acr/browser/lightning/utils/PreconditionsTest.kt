package fulguris.utils

import org.junit.Test

/**
 * Unit tests for [Preconditions].
 */
class PreconditionsTest {

    @Test(expected = RuntimeException::class)
    fun `checkNonNull throws exception for null param`() = fulguris.utils.Preconditions.checkNonNull(null)

    @Test
    fun `checkNonNull succeeds for non null param`() = fulguris.utils.Preconditions.checkNonNull(Any())
}