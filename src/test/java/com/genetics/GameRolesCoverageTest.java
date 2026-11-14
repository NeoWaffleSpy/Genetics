package com.genetics;

import com.genetics.codex.MobGeneProfile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every creature in the installed game has a gene profile, so a game update that adds mobs fails here instead of
 * silently giving them the Default profile. Reads the game's Assets.zip (skipped when the game isn't installed).
 */
class GameRolesCoverageTest {
    /** Roles that are not creatures: props, cinematic and test actors, temple scenery, AI components. */
    private static final List<String> NOT_CREATURES_PREFIX = List.of("Temple_", "Cinematic_", "Component_", "Edible_",
            "Void_Anomaly_Crystal", "Empty_Role");
    private static final Set<String> NOT_CREATURES = Set.of("Bear_Voidtaken_Tentacle", "Bear_Voidtaken_Tentacle_Marker",
            "Goblin_Gobliterator_Explosive_Barrel", "Goblin_Lab_Scrapper", "Goblin_Scientist");
    /** Creatures that are not finished in the game (no model, can't be hurt): left out of the Catalogue. */
    private static final Set<String> UNFINISHED = Set.of("Bear_Voidtaken");

    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    @Test
    void everyCreatureRoleHasAProfile() throws IOException {
        Path zip = assetsZip();
        Assumptions.assumeTrue(zip != null && Files.exists(zip), "game Assets.zip not found");
        Set<String> profiled = new HashSet<>();
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) profiled.addAll(List.of(profile.getRoles()));

        Set<String> missing = new TreeSet<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(file.entries())) {
                String name = entry.getName();
                if (!name.startsWith("Server/NPC/Roles/") || !name.endsWith(".json")) continue;
                if (name.contains("/_Core/") || name.contains("/Components/") || name.contains("/Templates/")) continue;
                String role = name.substring(name.lastIndexOf('/') + 1, name.length() - 5);
                String text = new String(file.getInputStream(entry).readAllBytes());
                if (text.contains("\"Type\": \"Abstract\"") || text.contains("\"Type\":\"Abstract\"")) continue;
                // Placeholders (no real model yet) and deprecated roles are not finished creatures.
                if (text.contains("Template_Placeholder") || name.contains("/_Deprecated/") || UNFINISHED.contains(role)) {
                    if (profiled.contains(role)) missing.add(role + " (unfinished, should not have a profile)");
                    continue;
                }
                if (NOT_CREATURES.contains(role) || NOT_CREATURES_PREFIX.stream().anyMatch(role::startsWith)) continue;
                if (!profiled.contains(role)) missing.add(role);
            }
        }
        assertEquals(List.of(), new ArrayList<>(missing), "NPC roles without a gene profile");
    }

    @Test
    void everyCatalogueIconExists() throws IOException {
        Path zip = assetsZip();
        Assumptions.assumeTrue(zip != null && Files.exists(zip), "game Assets.zip not found");
        Set<String> missing = new TreeSet<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
                for (String role : profile.getRoles()) {
                    String icon = profile.getIcon(role);
                    if (icon != null && file.getEntry("Common/" + icon) == null) missing.add(role + " -> " + icon);
                }
            }
        }
        assertEquals(List.of(), new ArrayList<>(missing), "Catalogue icons that are not in the game");
    }

    /** -Dgenetics.assets, else the default Hytale install under %APPDATA%. */
    private static Path assetsZip() {
        String prop = System.getProperty("genetics.assets");
        if (prop != null && !prop.isBlank()) return Path.of(prop);
        String appData = System.getenv("APPDATA");
        return appData != null ? Path.of(appData, "Hytale", "install", "release", "package", "game", "latest", "Assets.zip") : null;
    }
}
