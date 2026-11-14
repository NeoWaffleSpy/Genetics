package com.genetics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Custom UI selectors must use single spaces: "#List[0]  #Name" crashes the client with "Index was outside the bounds
 * of the array". Catches a doubled space before a "#" inside a string literal, and a literal ending in a space joined
 * to one starting with a space (row + " " + " #Max").
 */
class UiSelectorsTest {
    private static final Pattern DOUBLE_IN_LITERAL = Pattern.compile("\"[^\"\\n]*[\\]A-Za-z0-9]  +#[^\"\\n]*\"");
    private static final Pattern SPACE_JOIN = Pattern.compile(" \"\\s*\\+\\s*\" #");

    @Test
    void selectorsHaveNoDoubledSpaces() throws IOException {
        List<String> problems = new ArrayList<>();
        Path sources = TestAssets.resources().getParent().resolve("java/com/genetics");
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (DOUBLE_IN_LITERAL.matcher(line).find() || SPACE_JOIN.matcher(line).find()) {
                        problems.add(file.getFileName() + ":" + (i + 1) + "  " + line.trim());
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }
}
