# coho-template-kotlin

The starting point for your content library: a Kotlin library, generated from your Coho CMS project's content types, that your sites and services depend on. Each content type becomes a class in its own file, and the library's client returns them.

It is a skeleton. `coho-ir.json` is an empty placeholder type model, and `src/main/kotlin/com/yourorg/content/coho/Coho.kt` is what it generates: no content types, only the untyped reads, so the template builds as it stands. Generating yours in step 3 replaces both, with your project's type model and a class per content type.

## Set up

You need a JDK, 21 or later. The library runs on Java 17 and later. Gradle comes with it, as `./gradlew`, and brings the Kotlin compiler.

1. **Copy the template** into a folder named for your library, which names it, and make it a repository of its own:

   ```bash
   git clone --depth 1 https://github.com/coho-cms/coho-template-kotlin acme-content
   cd acme-content
   rm -rf .git && git init
   ```

   Or create your repository from this template on GitHub.

2. **Point it at your project** with a project token with read access, in `.env`, which is in `.gitignore`. Coho says which account and project the token is for, so there are no ids to copy. `--package` renames the template's package, `com.yourorg.content`, everywhere it appears:

   ```bash
   coho token create --role viewer --label "acme-content library"
   cp .env.example .env        # and set COHO_TOKEN in it
   ./gradlew cohoInit --args="--url https://app.coho.example --ref v1.4.0 --package com.acme.content"
   ```

   For CI, set the same token as the `COHO_TOKEN` secret.

3. **Generate your types**, and commit the type model and the generated code:

   ```bash
   ./gradlew cohoPull cohoGenerate
   ./gradlew build
   ```

4. **Choose where it is published.** The release workflow publishes to GitHub Packages, under the repository's owner; change the repository in `build.gradle.kts` for another. The library's group is its package, and its name is its folder's.

## See what's there

The catalog lists every content type and how many entries it has, read live from delivery with a delivery key, the way an app reads it. Add these to `.env`:

```
COHO_DELIVERY_URL=https://delivery.coho.example
COHO_DELIVERY_KEY=<a delivery key for the project>
COHO_REF=qa
```

`COHO_REF` is the tag or environment to read; leave it empty to read the ref in `coho.config.json`. Then:

```bash
./gradlew catalog
```

```
Content on qa:

  blogPost     Blog post         2
  tag          Tag               3
  teamMember   Team member       2

  7 entries in 3 types
```

To see what is on `dev` or a branch, before it is released, add `--preview`. It reads live through Coho with the `COHO_TOKEN` you pull with, and needs no delivery key:

```bash
./gradlew catalog --args=--preview
./gradlew catalog --args="--preview --ref feature/spring-campaign"
```

Its code is in `src/tools`, a source set of its own, so it is never in the library.

## When the content types change

Cut a tag in Coho, then:

```bash
./gradlew cohoPull --args="--ref v1.4.0" cohoGenerate
```

Review and commit the diff. `./gradlew check`, which CI runs, fails whenever the generated code doesn't match the committed type model.

To release, run the **Release** workflow with the Coho tag and the version to publish. It pulls that tag's types, generates, builds, tests and publishes.

Your own reads go in `CohoReads.kt`, built on the generated ones. The `coho` package is regenerated and never edited, so a schema change that breaks one of your reads fails this library's build, not an app.

## Using the library in an app

For a project with a `blogPost` type:

```kotlin
implementation("com.acme.content:acme-content:1.4.0")
```

```kotlin
val coho = Coho(CohoEnvironment.fromEnvironment())

val latest = coho.blogPost.list(ListOptions(order = BlogPost.SortBy.PublishedAt.descending, limit = 3, depth = 1))
val home = coho.homepage()

// In a coroutine, such as a Ktor route: each read has an Async form to await.
val post = coho.blogPost.bySlugAsync("hello-world", ReadOptions(depth = 1)).await()
```

```
# Released content, in production: set as the app's environment.
COHO_DELIVERY_URL=https://delivery.coho.example
COHO_DELIVERY_KEY=<a delivery key>
COHO_PROJECT=0192…
COHO_REF=prod
COHO_LOCALE=en-US
```

While you develop an app beside the library, `includeBuild("../acme-content")` in the app's `settings.gradle.kts` builds the library from its folder, with no version in the dependency.

### Previews

To show `dev` or a branch before it is released, such as on a developer's machine or a preview deployment, read it with a project token instead of a delivery key. Only the configuration changes:

```
COHO_URL=https://api.coho.example
COHO_ACCOUNT=0191…
COHO_PROJECT=0192…
COHO_PREVIEW_TOKEN=coho_pt_…
COHO_REF=dev
```

The answers are the same shapes, so the app's code doesn't change. Setting a preview token and a delivery key together is refused.

## Three credentials, kept apart

| | Who holds it | For |
|---|---|---|
| `COHO_TOKEN` | This library's CI, and you when you pull | Reading the content types. Never deployed |
| A delivery key | Each app, on the server | Reading released content at runtime |
| A preview token | An app, on the server, wherever it previews | Reading unreleased content. A project token with the viewer role: `coho token create --role viewer` |

The library reads no configuration of its own, so a key or token is only ever where the app runs, which passes it in. Keep them on the server: a preview token reads everything unreleased.

## License

Apache-2.0
