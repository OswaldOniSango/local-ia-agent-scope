package dev.oswaldo.localai.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectReaderServiceTest {

    @Test
    void findsExactRequestedFilename() throws Exception {
        Path root = Files.createTempDirectory("project-reader-test");
        Files.writeString(root.resolve("Dijkstra.java"), "class Dijkstra { void run() {} }");
        Files.writeString(root.resolve("Other.java"), "class Other {}");

        ProjectReaderService reader = new ProjectReaderService(root);
        List<ProjectFile> files = reader.search("show Dijkstra.java methods", 5);

        assertEquals(1, files.size());
        assertEquals("Dijkstra.java", files.get(0).path());
    }

    @Test
    void returnsOverviewWhenNoSpecificMatchExists() throws Exception {
        Path root = Files.createTempDirectory("project-reader-overview-test");
        Files.writeString(root.resolve("README.md"), "# Demo");

        ProjectReaderService reader = new ProjectReaderService(root);
        List<ProjectFile> files = reader.search("explain this project", 5);

        assertFalse(files.isEmpty());
        assertEquals("README.md", files.get(0).path());
    }
}
