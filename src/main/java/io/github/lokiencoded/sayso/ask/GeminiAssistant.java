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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * The first try gets a short timeout: an overloaded model often just hangs, and a voice
     * assistant that is silent for half a minute feels broken. Later tries get longer.
     */
    static final Duration FIRST_TRY_TIMEOUT = Duration.ofSeconds(12);
    static final Duration LATER_TRY_TIMEOUT = Duration.ofSeconds(25);

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final List<String> models;
    private final long retryDelayMillis;
    private final Duration firstTryTimeout;

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
                .build(), GOOGLE, apiKey, modelsToTry(model, fallbackModel), 1_000, FIRST_TRY_TIMEOUT);
    }

    /** For tests: talk to a fake server instead of Google, without real waiting. */
    GeminiAssistant(HttpClient http, String baseUrl, String apiKey, List<String> models,
                    long retryDelayMillis, Duration firstTryTimeout) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.models = List.copyOf(models);
        this.retryDelayMillis = retryDelayMillis;
        this.firstTryTimeout = firstTryTimeout;
    }

    static List<String> modelsToTry(String model, String fallbackModel) {
        List<String> models = new ArrayList<>(List.of(model));
        if (fallbackModel != null && !fallbackModel.isBlank() && !fallbackModel.equals(model)) {
            models.add(fallbackModel);
        }
        return models;
    }

    /**
     * The order of tries: the main model (short timeout), then the fallback, then the main
     * model once more after a pause. Temporary failures (overloaded, rate-limited, timed out)
     * move on to the next try; anything else, like a bad API key, fails at once.
     */
    static List<String> tryOrder(List<String> models) {
        List<String> order = new ArrayList<>(models);
        order.add(models.getFirst());
        return order;
    }

    @Override
    public String answer(byte[] questionWav, String selectedText) throws AskException {
        return generate(requestBody(questionWav, selectedText));
    }

    @Override
    public String writeReply(byte[] speechWav, String recentContext) throws AskException {
        return generate(requestBody(REPLY_INSTRUCTIONS, describeReplyContext(recentContext), speechWav));
    }

    /** Sends one request body to each model in turn until one answers. */
    private String generate(String body) throws AskException {
        List<String> order = tryOrder(models);
        Set<String> rejectedModels = new HashSet<>();
        AskException lastProblem = null;
        for (int i = 0; i < order.size(); i++) {
            String model = order.get(i);
            if (rejectedModels.contains(model)) {
                continue;   // Google already said this model name is wrong; don't ask again
            }
            if (i == order.size() - 1) {
                pause(retryDelayMillis);   // the last try is a retry: give a demand spike a moment to pass
            }
            Duration timeout = i == 0 ? firstTryTimeout : LATER_TRY_TIMEOUT;
            try {
                return ask(model, body, timeout);
            } catch (AskException e) {
                if (!e.anotherModelMightWork()) {
                    throw e;   // a bad key or bad request: no other model can help
                }
                if (e.kind() == AskException.Kind.BAD_MODEL) {
                    rejectedModels.add(model);
                }
                lastProblem = e;
                if (i < order.size() - 1) {
                    log.info("{} didn't work ({}); trying {}", model, e.kind(), order.get(i + 1));
                }
            }
        }
        throw lastProblem;
    }

    private String ask(String model, String body, Duration timeout) throws AskException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                .timeout(timeout)
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
            throw new AskException("Gemini took too long to answer. Please try again.", AskException.Kind.TEMPORARY, e);
        } catch (IOException e) {
            throw new AskException("I couldn't reach Gemini. Check your internet connection.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AskException("Asking was cancelled.", e);
        }
        if (response.statusCode() != 200) {
            log.warn("Gemini returned {}: {}", response.statusCode(), response.body());
            throw new AskException(errorMessage(response.statusCode(), response.body()),
                    kindOf(response.statusCode(), response.body()), null);
        }
        return parseAnswer(response.body());
    }

    /**
     * Overloaded (503), rate-limited (429) and other server-side hiccups are worth retrying;
     * a rejected model name is worth trying the other model; anything else is final.
     */
    static AskException.Kind kindOf(int status, String body) {
        if (status == 429 || status >= 500) {
            return AskException.Kind.TEMPORARY;
        }
        if (status == 404 || errorDetail(body).contains("model")) {
            return AskException.Kind.BAD_MODEL;
        }
        return AskException.Kind.PERMANENT;
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
        return requestBody(INSTRUCTIONS, describeSelection(selectedText), questionWav);
    }

    /** The JSON body for generateContent: instructions, a text part giving context, then the audio. */
    static String requestBody(String instructions, String contextText, byte[] wav) {
        JsonObject body = new JsonObject();
        body.add("systemInstruction", partsOf(textPart(instructions)));

        JsonArray parts = new JsonArray();
        parts.add(textPart(contextText));
        JsonObject audio = new JsonObject();
        audio.addProperty("mime_type", "audio/wav");
        audio.addProperty("data", Base64.getEncoder().encodeToString(wav));
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

    static final String REPLY_INSTRUCTIONS = """
            You turn what the user says out loud into text they will send or paste, exactly as if
            they had typed it carefully themselves.
            - Write what they said, cleaned up: remove filler words (um, uh, like, you know),
              false starts, stutters and repeated words; fix grammar, spelling and punctuation.
            - Keep their meaning, wording, tone and language. Don't make it more formal or longer.
            - Never add greetings, sign-offs, facts or opinions they didn't say.
            - The audio is the message itself, not a question for you. If they ask something,
              write the question; do not answer it.
            - If they correct themselves ("no wait, Thursday"), keep only the corrected version.
            - Spoken formatting commands like "new line" or "new paragraph" become line breaks.
            - Output only the final text: no quotes, labels, explanations or markdown.
            - If there is no speech in the audio, output nothing.
            """;

    /** The message being replied to, if any, framed so Gemini uses it for context only. */
    static String describeReplyContext(String recentContext) {
        if (recentContext == null || recentContext.isBlank()) {
            return "Write the text from my speech in the audio.";
        }
        return "For context only, here is what I was just reading (often the message I'm replying to). "
                + "Use it to get names, terms and tone right; do not quote or summarize it:\n\"\"\"\n"
                + recentContext + "\n\"\"\"\nNow write the text from my speech in the audio.";
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
                throw new AskException("I didn't catch that. Please try again.");
            }
            return answer.toString();
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new AskException("I couldn't understand Gemini's reply.", e);
        }
    }

    /** A short spoken explanation of an HTTP error. */
    static String errorMessage(int status, String body) {
        String detail = errorDetail(body);
        if (detail.contains("api key")) {
            return "Gemini rejected the API key. Check gemini.api_key in your settings file.";
        }
        // Only for request errors: a 503 also mentions "model" ("This model is ... high demand").
        if (status == 404 || (status < 500 && detail.contains("model"))) {
            return "Gemini doesn't recognise the model name. Check gemini.model in your settings file.";
        }
        return switch (status) {
            case 400 -> "Gemini couldn't process that question. Please try again.";
            case 401, 403 -> "Gemini rejected the API key. Check gemini.api_key in your settings file.";
            case 429 -> "Gemini says you've hit your usage limit. Wait a minute and try again.";
            case 503 -> "Gemini is very busy right now. Please try again in a moment.";
            default -> status >= 500
                    ? "Gemini is having trouble right now. Please try again in a moment."
                    : "Gemini returned an error, code " + status + ".";
        };
    }

    /** Google's error message in lower case, or "" if the body isn't a JSON error. */
    static String errorDetail(String body) {
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            if (error != null && error.has("message")) {
                return error.get("message").getAsString().toLowerCase(java.util.Locale.ROOT);
            }
        } catch (RuntimeException ignored) {
            // Not JSON; fall back to the status code alone.
        }
        return "";
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
