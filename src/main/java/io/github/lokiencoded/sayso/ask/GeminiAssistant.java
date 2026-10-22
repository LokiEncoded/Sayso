package io.github.lokiencoded.sayso.ask;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Asks Google Gemini. One request carries both the recorded question (Gemini understands
 * audio directly, so no separate speech-to-text step) and the selected text.
 *
 * <p>Uses the generateContent REST endpoint with Java's built-in HTTP client.
 */
public final class GeminiAssistant implements Assistant {

    private static final Logger log = LoggerFactory.getLogger(GeminiAssistant.class);

    private static final String GOOGLE = "https://generativelanguage.googleapis.com";

    /** Long pages are trimmed so a request stays fast and well inside the model's limits. */
    static final int MAX_SELECTION_CHARS = 60_000;

    static final String INSTRUCTIONS = """
            You are Sayso, a voice assistant. Your answer will be read aloud by a text-to-speech voice.
            The user selected some text on their screen (it may be empty) and asked a question out loud.
            The question is in the audio. Answer it, using the selected text when it is relevant.
            Rules for spoken answers:
            - Plain sentences only. No markdown, bullet points, headings, tables, emoji or code blocks.
            - Be brief: two to four sentences unless the user asks for more detail.
            - Reply in the language the user spoke.
            - If the audio has no clear question, say so in one short sentence.
            """;

    /** Tries per model before moving on; Google's "high demand" spikes usually pass within seconds. */
    static final int ATTEMPTS_PER_MODEL = 2;

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final List<String> models;
    private final long retryDelayMillis;

    /**
     * @param model         the model to ask first
     * @param fallbackModel asked when the first is overloaded or rate-limited; null for none
     */
    public GeminiAssistant(String apiKey, String model, String fallbackModel) {
        // HTTP/1.1 on purpose: with HTTP/2, a request bigger than the 64 KB flow-control window
        // (a few seconds of audio is ~150 KB) stalled in Java's HttpClient until it timed out.
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build(), GOOGLE, apiKey, modelsToTry(model, fallbackModel), 1_000);
    }

    /** For tests: talk to a fake server instead of Google, without real waiting. */
    GeminiAssistant(HttpClient http, String baseUrl, String apiKey, List<String> models, long retryDelayMillis) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.models = List.copyOf(models);
        this.retryDelayMillis = retryDelayMillis;
    }

    static List<String> modelsToTry(String model, String fallbackModel) {
        List<String> models = new ArrayList<>(List.of(model));
        if (fallbackModel != null && !fallbackModel.isBlank() && !fallbackModel.equals(model)) {
            models.add(fallbackModel);
        }
        return models;
    }

    /**
     * Asks each model in turn. Temporary failures (overloaded, rate-limited, timed out) are
     * retried after a short pause and then handed to the fallback model; anything else,
     * like a bad API key, fails at once because retrying can't help.
     */
    @Override
    public String answer(byte[] questionWav, String selectedText) throws AskException {
        String body = requestBody(questionWav, selectedText);
        AskException lastProblem = null;
        for (String model : models) {
            for (int attempt = 1; attempt <= ATTEMPTS_PER_MODEL; attempt++) {
                try {
                    return ask(model, body);
                } catch (AskException e) {
                    if (!e.isTemporary()) {
                        throw e;
                    }
                    lastProblem = e;
                    if (attempt < ATTEMPTS_PER_MODEL) {
                        pause(retryDelayMillis * attempt);
                    }
                }
            }
            log.info("{} is unavailable right now; trying the next model", model);
        }
        throw lastProblem;
    }

    private String ask(String model, String body) throws AskException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        log.info("Sending {} KB to Gemini ({})", body.length() / 1024, model);
        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("Gemini replied {} in {} ms", response.statusCode(), (System.nanoTime() - started) / 1_000_000);
        } catch (HttpTimeoutException e) {
            log.warn("No reply from Gemini after {} ms", (System.nanoTime() - started) / 1_000_000);
            throw new AskException("Gemini took too long to answer. Please try again.", true, e);
        } catch (IOException e) {
            throw new AskException("I couldn't reach Gemini. Check your internet connection.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AskException("Asking was cancelled.", e);
        }
        if (response.statusCode() != 200) {
            log.warn("Gemini returned {}: {}", response.statusCode(), response.body());
            throw new AskException(errorMessage(response.statusCode(), response.body()),
                    isTemporary(response.statusCode()), null);
        }
        return parseAnswer(response.body());
    }

    /** Overloaded (503), rate-limited (429) and other server-side hiccups are worth retrying. */
    static boolean isTemporary(int status) {
        return status == 429 || status >= 500;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The JSON body for generateContent: instructions, the selected text, then the audio question. */
    static String requestBody(byte[] questionWav, String selectedText) {
        JsonObject body = new JsonObject();
        body.add("systemInstruction", partsOf(textPart(INSTRUCTIONS)));

        JsonArray parts = new JsonArray();
        parts.add(textPart(describeSelection(selectedText)));
        JsonObject audio = new JsonObject();
        audio.addProperty("mime_type", "audio/wav");
        audio.addProperty("data", Base64.getEncoder().encodeToString(questionWav));
        JsonObject audioPart = new JsonObject();
        audioPart.add("inline_data", audio);
        parts.add(audioPart);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(user);
        body.add("contents", contents);

        JsonObject config = new JsonObject();
        config.addProperty("temperature", 0.4);
        body.add("generationConfig", config);
        return body.toString();
    }

    static String describeSelection(String selectedText) {
        if (selectedText == null || selectedText.isBlank()) {
            return "No text is selected. My question is in the audio.";
        }
        String text = selectedText.length() > MAX_SELECTION_CHARS
                ? selectedText.substring(0, MAX_SELECTION_CHARS) + " [...]"
                : selectedText;
        return "Selected text:\n\"\"\"\n" + text + "\n\"\"\"\nMy question about it is in the audio.";
    }

    /** Joins the text parts of the first candidate, skipping the model's internal "thought" parts. */
    static String parseAnswer(String responseJson) throws AskException {
        try {
            JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new AskException("Gemini didn't answer that one. Try asking another way.");
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            StringBuilder answer = new StringBuilder();
            if (content != null && content.has("parts")) {
                for (JsonElement part : content.getAsJsonArray("parts")) {
                    JsonObject object = part.getAsJsonObject();
                    boolean thought = object.has("thought") && object.get("thought").getAsBoolean();
                    if (!thought && object.has("text")) {
                        answer.append(object.get("text").getAsString());
                    }
                }
            }
            if (answer.isEmpty()) {
                throw new AskException("Gemini sent back an empty answer. Please try again.");
            }
            return answer.toString();
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new AskException("I couldn't understand Gemini's reply.", e);
        }
    }

    /** A short spoken explanation of an HTTP error. */
    static String errorMessage(int status, String body) {
        String detail = "";
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            if (error != null && error.has("message")) {
                detail = error.get("message").getAsString().toLowerCase();
            }
        } catch (RuntimeException ignored) {
            // Not JSON; fall back to the status code alone.
        }
        if (detail.contains("api key")) {
            return "Gemini rejected the API key. Check gemini.api_key in your settings file.";
        }
        return switch (status) {
            case 400 -> "Gemini couldn't process that question. Please try again.";
            case 401, 403 -> "Gemini rejected the API key. Check gemini.api_key in your settings file.";
            case 404 -> "Gemini doesn't recognise that model. Check gemini.model in your settings file.";
            case 429 -> "Gemini says you've hit your usage limit. Wait a minute and try again.";
            case 503 -> "Gemini is very busy right now. Please try again in a moment.";
            default -> status >= 500
                    ? "Gemini is having trouble right now. Please try again in a moment."
                    : "Gemini returned an error, code " + status + ".";
        };
    }

    private static JsonObject textPart(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        return part;
    }

    private static JsonObject partsOf(JsonObject part) {
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject wrapper = new JsonObject();
        wrapper.add("parts", parts);
        return wrapper;
    }
}
