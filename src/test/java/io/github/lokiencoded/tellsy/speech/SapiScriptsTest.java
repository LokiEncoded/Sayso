package io.github.lokiencoded.tellsy.speech;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SapiScriptsTest {

    @Test
    void setsTheRequestedRate() {
        assertTrue(SapiScripts.speak(3, null).contains("$s.Rate = 3"));
    }

    @Test
    void clampsRateToTheEngineRange() {
        assertTrue(SapiScripts.speak(50, null).contains("$s.Rate = 10"));
        assertTrue(SapiScripts.speak(-50, null).contains("$s.Rate = -10"));
    }

    @Test
    void usesDefaultVoiceWhenNoneIsGiven() {
        assertFalse(SapiScripts.speak(0, null).contains("SelectVoice"));
        assertFalse(SapiScripts.speak(0, "  ").contains("SelectVoice"));
    }

    @Test
    void escapesQuotesInVoiceNames() {
        String script = SapiScripts.speak(0, "Bob's Voice");
        assertTrue(script.contains("$s.SelectVoice('Bob''s Voice')"));
    }

    @Test
    void encodesAsBase64OfUtf16LittleEndian() {
        String script = "Write-Output 'hi'";
        byte[] decoded = Base64.getDecoder().decode(SapiScripts.encode(script));
        assertEquals(script, new String(decoded, StandardCharsets.UTF_16LE));
    }
}
