package io.github.lokiencoded.sayso.config;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelNamesTest {

    @Test
    void plainNamesPassThrough() {
        assertEquals(Optional.of("gemini-3.5-flash-lite"), ModelNames.clean("gemini-3.5-flash-lite"));
    }

    /** The real slip from the laptop: the whole line pasted after the equals sign. */
    @Test
    void forgivesTheSettingNamePastedTwice() {
        assertEquals(Optional.of("gemini-3.5-flash-lite"), ModelNames.clean("gemini.model=gemini-3.5-flash-lite"));
    }

    @Test
    void forgivesQuotesSpacesAndTheModelsPrefix() {
        assertEquals(Optional.of("gemini-flash-latest"), ModelNames.clean("  \"models/gemini-flash-latest\" "));
    }

    @Test
    void rejectsThingsThatAreNotModelNames() {
        assertEquals(Optional.empty(), ModelNames.clean("gemini flash"));
        assertEquals(Optional.empty(), ModelNames.clean("https://example.com/model"));
        assertEquals(Optional.empty(), ModelNames.clean(""));
    }
}
