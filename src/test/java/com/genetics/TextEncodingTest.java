package com.genetics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Data and language files are UTF-8 without mojibake: text decoded with the wrong charset and saved again turns
 * "·" into "Â·" (or the escaped "Â·"), which then shows in game.
 */
class TextEncodingTest {
    private static final Pattern MOJIBAKE = Pattern.compile("Â|Ã|â€|\\\\u00c[23]\\\\u00[89ab]", Pattern.CASE_INSENSITIVE);

    @Test
    void noMojibakeInDataFiles() throws IOException {
        List<String> problems = new ArrayList<>();
        try (var files = Files.walk(TestAssets.resources())) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".lang")).toList()) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (MOJIBAKE.matcher(text).find()) problems.add(TestAssets.resources().relativize(file).toString());
            }
        }
        assertEquals(List.of(), problems);
    }
}
