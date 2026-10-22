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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

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
        assertTrue(GeminiAssistant.errorMessage(503, "not json").contains("very busy"));
        assertTrue(GeminiAssistant.errorMessage(500, "not json").contains("trouble"));
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
                    "http://localhost:" + server.getAddress().getPort(), "test-key",
                    List.of("gemini-flash-latest"), 0, Duration.ofSeconds(5));

            String answer = gemini.answer(AUDIO, "Some selected text");

            assertEquals("**Yes**, it does.", answer);   // markdown is cleaned later, by SpeakableText
            assertEquals("/v1beta/models/gemini-flash-latest:generateContent", path.get());
            assertEquals("test-key", key.get());
            assertTrue(body.get().contains("Some selected text"), body.get());
        } finally {
            server.stop(0);
        }
    }

    /** A long question is well over HTTP/2's 64 KB window; it must still go through in one piece. */
    @Test
    void sendsLongRecordingsInFull() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = fakeGemini(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}",
                new AtomicReference<>(), new AtomicReference<>(), body);
        try {
            HttpClient http1 = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            GeminiAssistant gemini = new GeminiAssistant(http1,
                    "http://localhost:" + server.getAddress().getPort(), "k", List.of("m"), 0, Duration.ofSeconds(5));
            byte[] tenSeconds = new byte[320_000];

            assertEquals("ok", gemini.answer(tenSeconds, ""));
            assertTrue(body.get().contains(Base64.getEncoder().encodeToString(tenSeconds)));
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
                    "http://localhost:" + server.getAddress().getPort(), "k", List.of("m"), 0, Duration.ofSeconds(5));
            AskException e = assertThrows(AskException.class, () -> gemini.answer(AUDIO, ""));
            assertTrue(e.getMessage().contains("usage limit"), e.getMessage());
            assertTrue(e.isTemporary());
        } finally {
            server.stop(0);
        }
    }

    private static final String OK = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Answer from %s\"}]}}]}";
    private static final String BUSY = "{\"error\":{\"code\":503,\"message\":\"This model is currently experiencing high demand.\",\"status\":\"UNAVAILABLE\"}}";

    /** The real problem seen on the first day: a 503 "high demand" spike that passes quickly. */
    @Test
    void anOverloadedModelHandsOverToTheFallbackAtOnce() throws Exception {
        List<String> calls = new ArrayList<>();
        HttpServer server = scriptedGemini(calls, (model, attempt) -> model.equals("main") && attempt == 1 ? 503 : 200);
        try {
            assertEquals("Answer from lite", gemini(server, "main", "lite").answer(AUDIO, ""));
            assertEquals(List.of("main", "lite"), calls);   // straight to the fallback, no waiting
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToTheLighterModelWhenTheMainOneStaysBusy() throws Exception {
        List<String> calls = new ArrayList<>();
        HttpServer server = scriptedGemini(calls, (model, attempt) -> model.equals("main") ? 503 : 200);
        try {
            assertEquals("Answer from lite", gemini(server, "main", "lite").answer(AUDIO, ""));
            assertEquals(List.of("main", "lite"), calls);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aBadKeyIsNotRetried() throws Exception {
        List<String> calls = new ArrayList<>();
        HttpServer server = scriptedGemini(calls, (model, attempt) -> 403);
        try {
            AskException e = assertThrows(AskException.class, () -> gemini(server, "main", "lite").answer(AUDIO, ""));
            assertTrue(e.getMessage().contains("API key"), e.getMessage());
            assertEquals(List.of("main"), calls);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void everythingBusySaysSo() throws Exception {
        List<String> calls = new ArrayList<>();
        HttpServer server = scriptedGemini(calls, (model, attempt) -> 503);
        try {
            AskException e = assertThrows(AskException.class, () -> gemini(server, "main", "lite").answer(AUDIO, ""));
            assertTrue(e.getMessage().contains("very busy"), e.getMessage());
            assertEquals(List.of("main", "lite", "main"), calls);   // main, fallback, one retry, then give up
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesTheMainModelWhenThereIsNoFallback() throws Exception {
        List<String> calls = new ArrayList<>();
        HttpServer server = scriptedGemini(calls, (model, attempt) -> attempt == 1 ? 503 : 200);
        try {
            assertEquals("Answer from main", gemini(server, "main", null).answer(AUDIO, ""));
            assertEquals(List.of("main", "main"), calls);
        } finally {
            server.stop(0);
        }
    }

    /** Seen on the laptop: the main model hung for 30 s before saying it was overloaded. */
    @Test
    void aHangingModelIsAbandonedQuickly() throws Exception {
        List<String> calls = new ArrayList<>();
        HttpServer server = scriptedGemini(calls, (model, attempt) -> {
            if (model.equals("main")) {
                pauseFor(3_000);   // hangs far longer than the first-try timeout below
            }
            return 200;
        });
        try {
            GeminiAssistant gemini = new GeminiAssistant(HttpClient.newHttpClient(),
                    "http://localhost:" + server.getAddress().getPort(), "k",
                    List.of("main", "lite"), 0, Duration.ofMillis(300));
            long started = System.nanoTime();
            assertEquals("Answer from lite", gemini.answer(AUDIO, ""));
            assertTrue((System.nanoTime() - started) / 1_000_000 < 2_500, "should not wait for the hung model");
        } finally {
            server.stop(0);
        }
    }

    private static void pauseFor(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Seen on the laptop: a mistyped model name. The fallback still answers, and the bad one isn't retried. */
    @Test
    void aRejectedModelNameHandsOverToTheFallback() throws Exception {
        List<String> calls = new ArrayList<>();
        HttpServer server = scriptedGemini(calls, (model, attempt) -> model.equals("main") ? 400 : 200);
        try {
            assertEquals("Answer from lite", gemini(server, "main", "lite").answer(AUDIO, ""));
            assertEquals(List.of("main", "lite"), calls);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void errorsAreSortedByWhetherAnotherTryCanHelp() {
        String badModel = "{\"error\":{\"code\":400,\"message\":\"* GenerateContentRequest.model: unexpected model name format\"}}";
        String badKey = "{\"error\":{\"code\":400,\"message\":\"API key not valid.\"}}";
        assertEquals(AskException.Kind.BAD_MODEL, GeminiAssistant.kindOf(400, badModel));
        assertEquals(AskException.Kind.BAD_MODEL, GeminiAssistant.kindOf(404, ""));
        assertEquals(AskException.Kind.PERMANENT, GeminiAssistant.kindOf(400, badKey));
        assertEquals(AskException.Kind.TEMPORARY, GeminiAssistant.kindOf(503, ""));
        assertEquals(AskException.Kind.TEMPORARY, GeminiAssistant.kindOf(429, ""));
        assertTrue(GeminiAssistant.errorMessage(400, badModel).contains("gemini.model"));
    }

    @Test
    void fallbackIsOptional() {
        assertEquals(List.of("main"), GeminiAssistant.modelsToTry("main", null));
        assertEquals(List.of("main"), GeminiAssistant.modelsToTry("main", "main"));
        assertEquals(List.of("main", "lite"), GeminiAssistant.modelsToTry("main", "lite"));
    }

    private static GeminiAssistant gemini(HttpServer server, String model, String fallback) {
        return new GeminiAssistant(HttpClient.newHttpClient(), "http://localhost:" + server.getAddress().getPort(),
                "k", GeminiAssistant.modelsToTry(model, fallback), 0, Duration.ofSeconds(5));
    }

    /** A fake Gemini whose status code depends on the model asked and how many times it was asked. */
    private static HttpServer scriptedGemini(List<String> calls, BiFunction<String, Integer, Integer> status)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String model = path.substring(path.lastIndexOf('/') + 1, path.indexOf(':'));
            exchange.getRequestBody().readAllBytes();
            int attempt;
            synchronized (calls) {
                calls.add(model);
                attempt = (int) calls.stream().filter(model::equals).count();
            }
            int code = status.apply(model, attempt);
            String reply = switch (code) {
                case 200 -> OK.formatted(model);
                case 503 -> BUSY;
                case 400 -> "{\"error\":{\"code\":400,\"message\":\"* GenerateContentRequest.model: unexpected model name format\"}}";
                default -> "{\"error\":{\"code\":" + code + ",\"message\":\"Permission denied\"}}";
            };
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());   // a hung request must not block the next one
        server.start();
        return server;
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
