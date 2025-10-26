plugins {
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.paradaux"
version = providers.gradleProperty("version")
    .orElse("0.1.0-SNAPSHOT")
    .get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
//    withJavadocJar()
}

tasks.withType<Jar>().configureEach {
    // Reproducible builds
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper & MC-Specific dependencies
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // DI
    implementation("com.google.inject:guice:7.0.0")
    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("javax.inject:javax.inject:1")

    // Configurator
    implementation("org.reflections:reflections:0.10.2")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
}

/**
 * Optional: keep a shadowJar for your *local* testing,
 * but do NOT publish it. No relocations here — consumers handle that.
 */
tasks.shadowJar {
    archiveClassifier.set("shaded")
    // If you want to inspect a fat jar locally, you can minimize it — not published anyway.
    // minimize()
    // No relocations here — consumers will provide plugin-private namespaces.
}

// Ensure the plain jar remains the main artifact
tasks.jar { enabled = true }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])     // includes sources because of withSourcesJar()
            // DO NOT add: artifact(tasks.named("sourcesJar"))

            groupId = project.group.toString()
            artifactId = "hibernia-framework"
            version = project.version.toString()

            pom {
                name.set("hibernia-framework")
                description.set("Common core for Paradaux Minecraft plugins: Guice bootstrap + config/commands/events abstractions.")
                url.set("https://repo.paradaux.io")
                licenses {
                    license {
                        name.set("AGPL-3.0-or-later")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.en.html")
                        distribution.set("repo")
                    }
                }
                developers { developer { id.set("rian"); name.set("Rían Errity") } }
                scm {
                    url.set("https://github.com/ParadauxIO/hibernia-framework")
                    connection.set("scm:git:https://github.com/ParadauxIO/hibernia-framework.git")
                    developerConnection.set("scm:git:ssh://git@github.com/ParadauxIO/hibernia-framework.git")
                }
            }
        }
    }
    repositories {
        val isSnapshot = version.toString().endsWith("-SNAPSHOT")
        maven {
            name = if (isSnapshot) "ReposiliteSnapshots" else "ReposiliteReleases"
            url = uri(if (isSnapshot) "https://repo.paradaux.io/snapshots" else "https://repo.paradaux.io/releases")
            credentials {
                username = System.getenv("REPO_USER")
                password = System.getenv("REPO_PASS")
            }
        }
    }
}