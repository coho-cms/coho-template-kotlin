plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.4.21"
}

// The library's package, which `./gradlew cohoInit --args="--package …"` renames, here too. Its
// version is in gradle.properties, which the release workflow's -Pversion overrides.
group = "com.yourorg.content"

repositories()
{
    mavenCentral()
}

/** coho-codegen, which the coho* tasks run. A tool of the build's, never in the library. */
val cohoCodegen: Configuration = configurations.create("cohoCodegen")

dependencies()
{
    // In the API: the library's types derive from the runtime's, and apps call it.
    api("com.coho-cms:coho-delivery:2026.2.0-alpha.1")
    cohoCodegen("com.coho-cms:coho-codegen:2026.2.0-alpha.1")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java()
{
    withSourcesJar()
}

kotlin()
{
    jvmToolchain(21)
    compilerOptions()
    {
        // For apps on Java 17 and later.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach()
{
    options.release.set(17)
}

tasks.test()
{
    useJUnitPlatform()
}

/* COHO */
/* COHO */
/* COHO */

// coho-codegen, as tasks: `./gradlew cohoPull cohoGenerate`. Options go in --args, such as
// `./gradlew cohoPull --args="--ref v1.4.0"`.
listOf(
    "cohoInit" to "init",
    "cohoPull" to "pull",
    "cohoGenerate" to "generate",
    "cohoCheck" to "check",
).forEach()
{ (name, command) ->
    tasks.register<JavaExec>(name)
    {
        group = "coho"
        description = "coho-codegen $command: see ./gradlew cohoHelp"
        classpath = cohoCodegen
        mainClass.set("com.cohocms.codegen.Main")
        // A provider, not args(): --args replaces args, and the command must stay.
        argumentProviders.add(CommandLineArgumentProvider { listOf(command) })
        workingDir = projectDir
    }
}

tasks.register<JavaExec>("cohoHelp")
{
    group = "coho"
    description = "What each coho task does"
    classpath = cohoCodegen
    mainClass.set("com.cohocms.codegen.Main")
    args("--help")
}

// CI's check fails when the generated code is not what the committed type model generates.
tasks.check()
{
    dependsOn("cohoCheck")
}

/* CATALOG */
/* CATALOG */
/* CATALOG */

// A development tool beside the library: `./gradlew catalog`, or `./gradlew catalog --args=--preview`.
// Its own source set, so it is never in the library's jar.
val tools: SourceSet = sourceSets.create("tools")
tools.compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
tools.runtimeClasspath += tools.output + tools.compileClasspath

tasks.register<JavaExec>("catalog")
{
    group = "coho"
    description = "Counts the entries of each content type: --args=--preview for dev, or --args=\"--ref <ref>\""
    classpath = tools.runtimeClasspath
    mainClass.set("com.yourorg.content.tools.CatalogKt")
    workingDir = projectDir
}

dependencies()
{
    testImplementation(tools.output)
}

/* PUBLISHING */
/* PUBLISHING */
/* PUBLISHING */

// The release workflow publishes to GitHub Packages, under the repository's owner. Change the
// repository for another registry.
publishing()
{
    publications()
    {
        create<MavenPublication>("library")
        {
            from(components["java"])
        }
    }
    repositories()
    {
        maven()
        {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "your-org/your-repo"}")
            credentials()
            {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
