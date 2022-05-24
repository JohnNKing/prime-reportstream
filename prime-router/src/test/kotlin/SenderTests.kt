package gov.cdc.prime.router

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import kotlin.test.Test

internal class SenderTests {
    @Test
    fun `test canonicalize sender name`() {
        assertThat(Sender.canonicalizeFullName("IGNORE")).isEqualTo("IGNORE.default")
        assertThat(Sender.canonicalizeFullName("IGNORE.default")).isEqualTo("IGNORE.default")
        assertThat { Sender.canonicalizeFullName("IGNORE.this.name.is.invalid") }
            .isFailure()
            .hasMessage("Internal Error: Invalid fullName: IGNORE.this.name.is.invalid")
    }
}