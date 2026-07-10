package dev.oswaldo.localai.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectReaderService {

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", ".gradle", ".idea", ".mypy_cache", ".pytest_cache", ".ruff_cache", ".venv",
            "__pycache__", "build", "models", "node_modules", "out", "target", "venv"
    );
    private static final Set<String> INCLUDED_EXTENSIONS = Set.of(
            ".gradle", ".ini", ".java", ".json", ".md", ".properties", ".py", ".toml", ".txt", ".xml", ".yaml", ".yml"
    );
    private static final Set<String> OVERVIEW_FILENAMES = Set.of(
            "build.gradle", "build.gradle.kts", "pom.xml", "readme.md", "settings.gradle", "settings.gradle.kts"
    );
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9_]+ ");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("\\b[\\w.-]+\\.[A-Za-z0-9]+\\b");
    private static final int DEFAULT_MAX_FILE_BYTES = 80_000;

    private final Path root;
    private final int maxFileBytes;

    public ProjectReaderService(Path root) {
        this(root, DEFAULT_MAX_FILE_BYTES);
    }

    public ProjectReaderService(Path root, int maxFileBytes) {
        this.root = root.toAbsolutePath().normalize();
        this.maxFileBytes = maxFileBytes;
    }

    public List<ProjectFile> search(String question, int limit) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty.");
        }

        List<Path> exactPaths = exactFilenamePaths(question);
        if (!exactPaths.isEmpty()) {
            return readProjectFiles(exactPaths.stream().limit(limit).toList());
        }

        List<ScoredPath> scoredPaths = new ArrayList<>();
        for (Path path : candidatePaths()) {
            int score = scorePath(question, path);
            if (score > 0) {
                scoredPaths.add(new ScoredPath(score, path));
            }
        }

        List<Path> selectedPaths;
        if (scoredPaths.isEmpty()) {
            selectedPaths = overviewPaths(limit);
        } else {
            selectedPaths = scoredPaths.stream()
                    .sorted(Comparator.<ScoredPath>comparingInt(ScoredPath::score).reversed()
                            .thenComparing(item -> root.relativize(item.path()).toString()))
                    .limit(limit)
                    .map(ScoredPath::path)
                    .toList();
        }
        return readProjectFiles(selectedPaths);
    }

    private List<Path> exactFilenamePaths(String question) {
        Set<String> requested = requestedFilenames(question);
        if (requested.isEmpty()) {
            return List.of();
        }
        return candidatePaths().stream()
                .filter(path -> requested.contains(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .toList();
    }

    private List<Path> overviewPaths(int limit) {
        return candidatePaths().stream()
                .sorted(Comparator.comparing(this::overviewSortKey))
                .limit(limit)
                .toList();
    }

    private List<Path> candidatePaths() {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(path))
                    .filter(path -> INCLUDED_EXTENSIONS.contains(extension(path)))
                    .filter(this::isAllowedSize)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read project files from " + root, exception);
        }
    }

    private boolean isExcluded(Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (EXCLUDED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedSize(Path path) {
        try {
            return Files.size(path) <= maxFileBytes;
        } catch (IOException exception) {
            return false;
        }
    }

    private int scorePath(String question, Path path) {
        Set<String> queryTokens = tokens(question);
        if (queryTokens.isEmpty()) {
            return 0;
        }
        String relativePath = root.relativize(path).toString().toLowerCase(Locale.ROOT);
        Set<String> pathTokens = tokens(relativePath);
        int score = 0;
        for (String token : queryTokens) {
            if (pathTokens.contains(token)) {
                score += 5;
            }
        }

        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            return score;
        }
        for (String token : queryTokens) {
            if (content.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private List<ProjectFile> readProjectFiles(List<Path> paths) {
        List<ProjectFile> files = new ArrayList<>();
        for (Path path : paths) {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                files.add(new ProjectFile(root.relativize(path).toString(), languageForExtension(extension(path)), content));
            } catch (IOException ignored) {
                // Skip files that disappeared or cannot be decoded.
            }
        }
        return files;
    }

    private SortKey overviewSortKey(Path path) {
        String relativePath = root.relativize(path).toString().replace('\\', '/');
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String suffix = extension(path);
        int priority;
        if (OVERVIEW_FILENAMES.contains(filename)) {
            priority = 0;
        } else if (relativePath.contains("/src/main/") && Set.of(".java", ".py").contains(suffix)) {
            priority = 1;
        } else if (relativePath.contains("/src/") && Set.of(".java", ".py").contains(suffix)) {
            priority = 2;
        } else {
            priority = 3;
        }
        return new SortKey(priority, relativePath);
    }

    private static Set<String> tokens(String text) {
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("[a-zA-Z0-9_]+").matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 3) {
                values.add(token);
            }
        }
        return Set.copyOf(values);
    }

    private static Set<String> requestedFilenames(String text) {
        List<String> filenames = new ArrayList<>();
        Matcher matcher = FILENAME_PATTERN.matcher(text);
        while (matcher.find()) {
            filenames.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(filenames);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int index = name.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return name.substring(index).toLowerCase(Locale.ROOT);
    }

    private static String languageForExtension(String extension) {
        return switch (extension) {
            case ".gradle" -> "gradle";
            case ".java" -> "java";
            case ".json" -> "json";
            case ".md" -> "markdown";
            case ".properties" -> "properties";
            case ".py" -> "python";
            case ".toml" -> "toml";
            case ".xml" -> "xml";
            case ".yaml", ".yml" -> "yaml";
            default -> "text";
        };
    }

    private record ScoredPath(int score, Path path) {
    }

    private record SortKey(int priority, String path) implements Comparable<SortKey> {
        @Override
        public int compareTo(SortKey other) {
            int priorityComparison = Integer.compare(priority, other.priority);
            if (priorityComparison != 0) {
                return priorityComparison;
            }
            return path.compareTo(other.path);
        }
    }
}
