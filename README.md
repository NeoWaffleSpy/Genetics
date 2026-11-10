# Genetics

A genetics mod for Hytale. Early development.

Built from the [Hytale plugin template](https://github.com/Kaupenjoe/Hytale-Example-Plugin) using the
[Hytale Gradle Plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## Current content

- **Syringe** (`Server/Item/Items/Tools/Genetics_Syringe.json`):
  - **Left-click a mob** with an empty syringe to draw a raw sample. Genes are rolled from the mob's gene
    profile, and the syringe becomes "<Mob>'s Genes".
  - **Right-click with a filled syringe** to inject it into yourself. Each gene of a raw sample may come out
    as-is, weakened, dropped or turned into a drawback; the higher the Complexity, the worse the odds.
    Temporary effects start right away; stat genes and permanent effects become your **active genome**,
    replacing the previous one. The syringe is emptied.
  - **Right-click with an empty syringe** to copy your active genome into it, as a raw sample.
  - Model and icon are temporary placeholders.
- **Active genome**: saved on the player, kept through death and re-applied after respawn.
- **Lab Station** (`Server/Item/Items/Bench/Genetics_Lab_Station.json`): a one-slot station that only accepts
  syringes. Its custom screen (`Common/UI/Custom/Genetics/LabStation.ui`) shows the sample's source, Complexity,
  stat genes and effect genes, with drag & drop between the station and the inventory. Uses the Alchemy bench's
  model as a placeholder.
- **Effects**: `Genetics_Speed` (+20% movement speed) and `Genetics_NightVision` (a soft light around the player).
- **Data** (`Server/Genetics/`): the Effect Codex (stat weights, effect Complexity), the Mutation Codex and the
  mob gene profiles. All are asset types, so other packs can add or override entries; edits hot-reload.

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/com/genetics/GeneticsPlugin.java` | Plugin entry point, registers custom interactions |
| `src/main/java/com/genetics/interaction/` | Custom interaction types |
| `src/main/java/com/genetics/genome/` | Genomes: format, sampling, Complexity, injection, active genome |
| `src/main/java/com/genetics/codex/` | Loaders for the Codices and mob gene profiles |
| `src/main/java/com/genetics/effect/` | Behavior of custom effects |
| `src/main/java/com/genetics/labstation/` | Lab Station interaction and custom screen |
| `src/main/resources/Server/` | Server assets (items, effects, translations, Genetics data) |
| `src/main/resources/Common/UI/Custom/Genetics/` | Custom UI layouts |
| `gradle.properties` | Mod metadata; `manifest.json` is generated from it on every build |

## Development

```bash
# Generate decompiled sources for your IDE and download the game assets
./gradlew setupHytaleDev

# Launch a local server with this plugin loaded
./gradlew runServer

# Launch with a debugger attached and hot swap enabled
./gradlew runServer -Ddebug=true -Dhotswap=true
```

The first run asks you to sign in with your Hytale account so the assets can be downloaded.
Run `./gradlew hytaleDoctor` if something isn't resolving correctly.

Don't edit `src/main/resources/manifest.json` by hand. Change `gradle.properties` instead.

To use your installed copy of the game instead of downloading assets, set this in `%USERPROFILE%/.gradle/gradle.properties`:

```properties
hytale_home=path/to/Hytale
```

See the [plugin wiki](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki) for all options.
