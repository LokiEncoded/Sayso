package io.github.lokiencoded.sayso.ask;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiAssistantTest {

    private static final byte[] AUDIO = {1, 2, 3, 4};

    @Test
    void requestCarriesInstructionsSelectionAndAudio() {
        JsonObject body = JsonParser.parseString(
                GeminiAssistant.requestBody(AUDIO, "Photosynthesis turns light into sugar.")).getAsJsonObject();

        String instructions = body.getAsJsonObject("systemInstruction").getAsJsonArray("parts")
                .get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(instructions.contains("read aloud"), instructions);

        var parts = body.getAsJsonArray("contents").get(0).getAsJsonObject().getAsJsonArray("parts");
        assertTrue(parts.get(0).getAsJsonObject().get("text").getAsString().contains("Photosynthesis"));
        JsonObject audio = parts.get(1).getAsJsonObject().getAsJsonObject("inline_data");
        assertEquals("audio/wav", audio.get("mime_type").getAsString());
        assertEquals(Base64.getEncoder().encodeToString(AUDIO), audio.get("data").getAsString());
    }

    @Test
    void saysWhenNothingIsSelected() {
        assertTrue(GeminiAssistant.describeSelection("  ").startsWith("No text is selected"));
    }

    @Test
    void trimsVeryLongSelections() {
        String huge = "a".repeat(GeminiAssistant.MAX_SELECTION_CHARS + 500);
        assertTrue(GeminiAssistant.describeSelection(huge).length() < GeminiAssistant.MAX_SELECTION_CHARS + 200);
    }

    @Test
    void readsTheAnswerAndSkipsThoughts() throws AskException {
        String response = """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"Let me think about it.","thought":true},
                  {"text":"It means plants make food "},
                  {"text":"from sunlight."}]}}]}
                """;
        assertEquals("It means plants make food from sunlight.", GeminiAssistant.parseAnswer(response));
    }

    @Test
    void noCandidatesIsAFriendlyError() {
        AskException e = assertThrows(AskException.class,
                () -> GeminiAssistant.parseAnswer("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
        assertTrue(e.getMessage().contains("didn't answer"), e.getMessage());
    }

    @Test
    void garbledReplyIsAFriendlyError() {
        assertThrows(AskException.class, () -> GeminiAssistant.parseAnswer("<html>oops</html>"));
    }

    @Test
    void errorMessagesAreSpeakable() {
        String badKey = "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\"}}";
        assertTrue(GeminiAssistant.errorMessage(400, badKey).contains("API key"));
        assertTrue(GeminiAssistant.errorMessage(404, "").contains("gemini.model"));
        assertTrue(GeminiAssistant.errorMessage(429, "").contains("limit"));
        assertTrue(GeminiAssistant.errorMessage(503, "not json").contains("trouble"));
    }

    /** End to end against a local fake of the Gemini endpoint: URL, headers, body and reply. */
    @Test
    void talksToTheGenerateContentEndpoint() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = fakeGemini(200,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"**Yes**, it does.\"}]}}]}",
                path, key, body);
        try {
            GeminiAssistant gemini = new GeminiAssistant(HttpClient.newHttpClient(),
                    "http://localhost:" + server.getAddress().getPort(), "test-key", "gemini-flash-latest");

            String answer = gemini.answer(AUDIO, "Some selected text");

            assertEquals("**Yes**, it does.", answer);   // markdown is cleaned later, by SpeakableText
            assertEquals("/v1beta/models/gemini-flash-latest:generateContent", path.get());
            assertEquals("test-key", key.get());
            assertTrue(body.get().contains("Some selected text"), body.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpErrorsBecomeSpokenMessages() throws Exception {
        HttpServer server = fakeGemini(429, "{\"error\":{\"code\":429,\"message\":\"Quota exceeded\"}}",
                new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        try {
            GeminiAssistant gemini = new GeminiAssistant(HttpClient.newHttpClient(),
                    "http://localhost:" + server.getAddress().getPort(), "k", "m");
            AskException e = assertThrows(AskException.class, () -> gemini.answer(AUDIO, ""));
            assertTrue(e.getMessage().contains("usage limit"), e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer fakeGemini(int status, String reply, AtomicReference<String> path,
                                         AtomicReference<String> key, AtomicReference<String> body)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
