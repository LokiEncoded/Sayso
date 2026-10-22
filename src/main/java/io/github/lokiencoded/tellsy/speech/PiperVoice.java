package io.github.lokiencoded.tellsy.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * One Piper voice: a model file (en_US-lessac-medium.onnx) plus its config
 * (en_US-lessac-medium.onnx.json), which tells us the audio sample rate.
 */
public record PiperVoice(String name, Path model, int sampleRate) {

    static final int DEFAULT_SAMPLE_RATE = 22_050;

    private static final String MODEL_SUFFIX = ".onnx";
    private static final String CONFIG_SUFFIX = ".onnx.json";
    private static final Pattern SAMPLE_RATE = Pattern.compile("\"sample_rate\"\\s*:\\s*(\\d+)");

    /** Every usable voice in {@code dir}, sorted by name. Missing folder means no voices. */
    public static List<PiperVoice> discover(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<PiperVoice> voices = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path model : files.filter(PiperVoice::isModel).toList()) {
                load(model).ifPresent(voices::add);
            }
        } catch (IOException e) {
            return List.of();
        }
        voices.sort(Comparator.comparing(PiperVoice::name));
        return List.copyOf(voices);
    }

    private static boolean isModel(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith(MODEL_SUFFIX) && Files.isRegularFile(file);
    }

    /** A model only counts as a voice if its .onnx.json config sits next to it. */
    private static Optional<PiperVoice> load(Path model) {
        String fileName = model.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - MODEL_SUFFIX.length());
        Path config = model.resolveSibling(name + CONFIG_SUFFIX);
        if (!Files.isRegularFile(config)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PiperVoice(name, model, sampleRateFrom(Files.readString(config))));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Reads "audio": {"sample_rate": 22050} from a voice config, without a JSON library. */
    static int sampleRateFrom(String configJson) {
        Matcher matcher = SAMPLE_RATE.matcher(configJson);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : DEFAULT_SAMPLE_RATE;
    }
}
