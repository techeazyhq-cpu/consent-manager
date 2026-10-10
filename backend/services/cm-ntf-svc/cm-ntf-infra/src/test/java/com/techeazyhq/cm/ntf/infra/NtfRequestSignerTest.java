package com.techeazyhq.cm.ntf.infra;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NtfRequestSignerTest {

    @Test
    void matchesTheReferenceSignatureFromNotificationServicesClientGuide() {
        String signature = NtfRequestSigner.sign("nss_test", 1767225600L, "0123456789abcdef", "POST",
                "/v1/notifications", null, "{\"channel\":\"SMS\"}".getBytes(StandardCharsets.UTF_8));

        assertEquals("2295ae2e9e31c56816963cbcc04632396fa6471d25a8cb667f9003711e053503", signature);
    }
}
