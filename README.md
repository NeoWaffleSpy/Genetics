# Genetics

A genetics mod for Hytale. Early development.

Built from the [Hytale plugin template](https://github.com/Kaupenjoe/Hytale-Example-Plugin) using the
[Hytale Gradle Plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## Current content

- **Syringe** (`Server/Item/Items/Tools/Syringe.json`): primary-use on an NPC or player runs
  `UseSyringeInteraction`, which reports what was sampled and consumes one syringe.
  The item currently borrows the Crude Hammer's model, icon, recipe and translations as testing placeholders.

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/com/genetics/GeneticsPlugin.java` | Plugin entry point, registers custom interactions |
| `src/main/java/com/genetics/interaction/` | Custom interaction types |
| `src/main/resources/Server/` | Server assets (items, …) |
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
