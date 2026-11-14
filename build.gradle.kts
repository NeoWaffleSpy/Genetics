plugins {
    idea
    `maven-publish`
    alias(libs.plugins.hytale.tools)
}

group = "com.genetics"
version = "0.1.0"
val javaVersion = 25

// Repositories (Maven Central, the Hytale server repos, AzureDoom/HytaleModding mavens,
// CurseMaven, Modtale, Modifold) are added by the Hytale Gradle Plugin automatically.

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)

    // Unit tests only: nothing here is packaged into the mod jar.
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Tests run against the same Hytale server API the mod compiles against (compileOnly is not on the test
// classpath by default).
configurations.testImplementation { extendsFrom(configurations.compileOnly.get()) }

tasks.test {
    useJUnitPlatform()
    // The mod's data files, loaded by the tests through the real asset codecs.
    // Hytale classes log through HytaleLogger, which needs its log manager installed before anything else.
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
    systemProperty("genetics.resources", file("src/main/resources").absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Most values are read from gradle.properties (see the "Hytale" section there).
// Anything set here overrides the matching property.
hytaleTools {
    // Develop against the pre-release patchline instead of release.
    // patchline = "pre-release"

    // Pin the manifest's ServerVersion instead of using ">=<resolved server version>".
    // manifestServerVersion = "*"

    // Extra launch settings for ./gradlew runServer
    // serverJvmArgs("-Xms2G", "-Xmx2G")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Specification-Title"] = rootProject.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] =
            providers.environmentVariable("COMMIT_SHA_SHORT")
                .map { "${version}-${it}" }
                .getOrElse(version.toString())
    }
}

publishing {
    repositories {
        // This is where you put repositories that you want to publish to.
        // Do NOT put repositories for your dependencies here.
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
// The Hytale Gradle Plugin also attaches decompiled server sources and hosted Javadocs when the idea plugin is applied.
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
