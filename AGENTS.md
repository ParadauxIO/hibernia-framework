# AGENTS.md — HiberniaFramework

## Project Overview

Java 21 library providing a shared framework for PaperMC Minecraft plugins. Built with
Gradle 8.14 (Kotlin DSL). Provides Guice-based DI bootstrap, annotation-driven command
routing, reflective configuration injection, i18n, and domain exception types.

- **Group/Artifact:** `io.paradaux:hibernia-framework`
- **License:** AGPL-3.0-or-later
- **Source root:** `src/main/java/io/paradaux/hibernia/framework/`

## Build Commands

All commands use the Gradle wrapper. On Windows use `gradlew.bat`, on Unix use `./gradlew`.

```bash
# Full build (compile + test + assemble JAR)
./gradlew build

# Compile only (no tests/JAR)
./gradlew compileJava

# Clean build artifacts
./gradlew clean

# Fat/shaded JAR (classifier: "shaded")
./gradlew shadowJar

# Publish to Reposilite Maven repo (requires REPO_USER/REPO_PASS env vars)
./gradlew publish
```

## Test Commands

No test framework is currently configured. The `src/test/` directory does not exist.
Lombok test annotation processing is declared in `build.gradle.kts` for future use.

If tests are added (expected: JUnit 5), the commands would be:

```bash
# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "io.paradaux.hibernia.framework.utils.StringUtilsTest"

# Run a single test method
./gradlew test --tests "io.paradaux.hibernia.framework.utils.StringUtilsTest.testSanitize"

# Run tests matching a pattern
./gradlew test --tests "*StringUtils*"
```

## Linting / Static Analysis

No linting or static analysis tools are configured (no Checkstyle, SpotBugs, PMD,
or Error Prone). The compiler with Java 21 toolchain is the only validation.

## CI/CD

GitHub Actions workflow at `.github/workflows/publish.yml`:
- Triggers on push to `main` (release) or `develop` (snapshot), plus manual dispatch
- Runs: `./gradlew --no-daemon clean build publish -Pversion=<computed>`
- JDK: Temurin 21 with Gradle caching

## Project Structure

```
src/main/java/io/paradaux/hibernia/framework/
├── commander/                 # Command framework core
│   ├── CommandManager.java    # Main orchestrator (~460 lines)
│   ├── annotations/           # @Command, @Route, @Arg, @Sender, @Permission, @Async, etc.
│   ├── resolvers/             # BigDecimal, Integer, OfflinePlayer, String resolvers
│   └── spi/                   # CommandHandler, ParameterResolver<T>, SuggestionProvider
├── configurator/              # Annotation-based config injection
│   ├── ConfigurationLoader.java
│   ├── ConfigurationProcessor.java
│   └── annotations/           # @ConfigurationComponent, @ConfigurationValue
├── exceptions/                # Unchecked domain exceptions (6 classes)
├── i18n/                      # Message.java — i18n with MiniMessage
├── models/                    # HiberniaPlayer interface
└── utils/                     # StringUtils
```

## Key Dependencies

- `io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT` (compileOnly)
- `com.google.inject:guice:7.0.0` (DI)
- `com.google.guava:guava:33.2.1-jre`
- `org.reflections:reflections:0.10.2` (classpath scanning)
- `org.projectlombok:lombok:1.18.34` (compileOnly + annotation processor)

## Code Style Guidelines

### Formatting
- **Indentation:** 4 spaces, no tabs
- **Braces:** K&R style (opening brace on same line)
- **Line length:** ~120 characters soft limit
- **Single-line guards:** Allowed without braces (e.g., `if (x == null) continue;`)
- **Blank lines:** One blank line between methods; no excessive blanks within methods

### Imports
- **Two groups separated by a blank line:**
  1. Third-party and framework imports (`com.*`, `io.*`, `net.*`, `org.*`, `lombok.*`)
  2. Java standard library (`java.*`)
- Wildcard imports used selectively for closely related annotation groups
- All imports are fully qualified (absolute)

### Naming Conventions
| Element        | Convention         | Examples                                    |
|----------------|--------------------|---------------------------------------------|
| Classes        | PascalCase         | `CommandManager`, `ConfigurationProcessor`  |
| Interfaces     | PascalCase (no I)  | `CommandHandler`, `ParameterResolver<T>`    |
| Annotations    | PascalCase         | `@Command`, `@Route`, `@ConfigurationValue` |
| Methods        | camelCase          | `registerAll()`, `buildCommandTree()`       |
| Variables      | camelCase          | `classPerm`, `rootBuilder`, `matchingParam` |
| Constants      | UPPER_SNAKE_CASE   | `PLACEHOLDER_PREFIX`, `MM_TAG_REGEX`        |
| Packages       | lowercase          | `commander`, `annotations`, `resolvers`     |
| Exceptions     | PascalCase+Exception | `NotFoundException`, `ConflictException`  |

### Types and Generics
- Interfaces preferred over abstract classes for contracts
- Java records for internal value objects (e.g., `Segment`, `Param` in `CommandManager`)
- `Optional<T>` as return type only — never as a field type
- Bounded wildcards used idiomatically (`Map<Class<?>, ParameterResolver<?>>`)
- `@SuppressWarnings("unchecked")` applied locally when unavoidable casts are needed

### Error Handling
- Custom exceptions extend `RuntimeException` (unchecked), named after HTTP semantics:
  `BadCommandException`, `ConflictException`, `ExceedsLimitException`,
  `InternalException`, `NoPermissionException`, `NotFoundException`
- Try/catch: catch specific exceptions first (`InvocationTargetException`), then broad
  `Exception` as fallback
- User-facing error messages sent to `CommandSender`; stack traces logged server-side
- Try-with-resources for I/O streams

### Dependency Injection
- Google Guice with `@Inject` constructor injection (never field injection)
- `@Singleton` scope on service classes (`CommandManager`, `Message`, `ConfigurationLoader`)
- Guice multibinding via `Set<T>` constructor parameters for extensible registries

### Logging
- Lombok `@Slf4j` annotation preferred — generates `log` field
- Use `log.error(...)`, `log.warn(...)`, etc.
- Fallback: `plugin.getLogger().warning(...)` in classes without Lombok

### Visibility
- Fields: `private final` for dependencies and config; `private static final` for constants
- Methods: `public` for API surface only; `private` for internal implementation
- No `protected` methods — this project does not use inheritance hierarchies
- Inner types are `private` (records, static classes)

### Documentation
- Javadoc on all public API: classes, interfaces, annotations, public methods
- Use `<p>` for paragraphs, `<pre>` for code examples, `@param`/`@return`/`@throws` tags
- Inline `//` comments used sparingly for short explanations
- Avoid commented-out code

### Functional Patterns
- Lambdas and streams used freely for iteration, filtering, mapping
- `@FunctionalInterface` on single-method interfaces (e.g., `SuggestionProvider`)
- `var` used for local variables when the type is obvious from context

### File Organization
- One public type per file, filename matches class name
- Class member ordering: static constants → instance fields → constructor → public
  methods → private methods → inner types (records/classes) at bottom
- Strictly one `src/main/java` source root; no multi-module structure
