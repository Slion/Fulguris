package fulguris.adblock

import fulguris.adblock.parser.HostsFileParser
import fulguris.database.adblock.Host
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.InputStreamReader

/**
 * Unit tests for the assets ad blocker
 */
class HostsFileParserTest {

    @Test
    fun `line parsing is valid`() {
        val testInput = """
            127.0.0.1 localhost #comment comment
            ::1 localhost #comment comment
            #
            # another comment
            #
            127.0.0.1	fake.domain1.com
            127.0.0.1	fake.domain2.com    # comment
            0.0.0.0	fake.domain3.com    # comment
            # random comment
            ::1 domain4.com
            0.0.0.0 multiline1.com multiline2.com # comment
            0.0.0.0 comment.close.by.com#comment
            somedomain.com
            domain4.com domain5.com domain6.com
            ::1 ip6-localhost
            fe80::1%lo0 localhost
            ff00::0 ip6-localnet
            """

        val inputStreamReader = InputStreamReader(testInput.trimIndent().byteInputStream())
        val hostsFileParser = HostsFileParser()
        val mutableList = hostsFileParser.parseInput(inputStreamReader)
        val targetList = listOf(
            Host("fake.domain1.com"),
            Host("fake.domain2.com"),
            Host("fake.domain3.com"),
            Host("domain4.com"),
            Host("multiline1.com"),
            Host("multiline2.com"),
            Host("comment.close.by.com"),
            Host("somedomain.com"),
            Host("domain4.com"),
            Host("domain5.com"),
            Host("domain6.com"),
        )
        assertThat(mutableList).hasSize(targetList.size)
        assertThat(mutableList).containsAll(targetList)
    }
}
