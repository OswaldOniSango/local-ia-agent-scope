package dev.oswaldo.localai.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class AppConfig {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    private static final Map<String, String> LOCAL_ENV = loadLocalEnv();

    private AppConfig() {
    }

    public static String value(String name, String defaultValue) {
        String envValue = System.getenv(name);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String fileValue = LOCAL_ENV.get(name);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue;
        }
        return defaultValue;
    }

    public static int intValue(String name, int defaultValue) {
        String rawValue = value(name, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer.", exception);
        }
    }

    public static Path projectRoot() {
        return PROJECT_ROOT;
    }

    public static Path projectReaderRoot() {
        return Path.of(value("PROJECT_READER_ROOT", PROJECT_ROOT.toString())).toAbsolutePath().normalize();
    }

    private static Map<String, String> loadLocalEnv() {
        Path envPath = PROJECT_ROOT.resolve(".env");
        Map<String, String> values = new HashMap<>();
        if (!Files.isRegularFile(envPath)) {
            return values;
        }

        try {
            for (String line : Files.readAllLines(envPath)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                values.put(key, stripOptionalQuotes(value));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read local .env file: " + envPath, exception);
        }
        return values;
    }

    private static String stripOptionalQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
