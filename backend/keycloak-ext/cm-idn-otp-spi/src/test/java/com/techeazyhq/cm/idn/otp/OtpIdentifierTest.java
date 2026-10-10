package com.techeazyhq.cm.idn.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpIdentifierTest {

    @Test
    void anEmailIsTrimmedAndLowerCasedAndGoesByEmail() {
        OtpIdentifier id = OtpIdentifier.parse("  Ann.Lee+cm@Example.CO.in ").orElseThrow();

        assertEquals(OtpIdentifier.Kind.EMAIL, id.kind());
        assertEquals("ann.lee+cm@example.co.in", id.value());
        assertEquals("EMAIL", id.channel());
        assertEquals("a•••@example.co.in", id.masked());
    }

    @Test
    void aPhoneNumberLosesItsPunctuationAndGoesBySms() {
        OtpIdentifier id = OtpIdentifier.parse("+91 (98765) 432-10").orElseThrow();

        assertEquals(OtpIdentifier.Kind.PHONE, id.kind());
        assertEquals("+919876543210", id.value());
        assertEquals("SMS", id.channel());
        assertEquals("+91•••210", id.masked());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ann", "ann@", "@example.com", "ann@example", "9876543210", "+0123456789",
            "+12", "<script>@x.io", "ann\"@example.com", "ann@exa mple.com"})
    void anythingElseIsRefused(String input) {
        assertTrue(OtpIdentifier.parse(input).isEmpty(), input);
    }

    @Test
    void nullIsRefused() {
        assertTrue(OtpIdentifier.parse(null).isEmpty());
    }
}
