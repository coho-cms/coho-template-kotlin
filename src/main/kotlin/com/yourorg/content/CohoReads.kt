package com.yourorg.content

import com.cohocms.delivery.EntryBase
import com.cohocms.delivery.ReadOptions
import com.yourorg.content.coho.Coho

// Reads of your own, built on the generated ones.
//
// The `coho` package is generated from the project's content types: after a schema change, run
// `./gradlew cohoPull cohoGenerate`, and never edit it by hand. Your reads belong here, beside
// it. Regeneration never touches this file, and a schema change that removes something these
// reads use fails this library's build rather than an app at runtime.

/**
 * An example of a read of your own: the home page, and everything it links to two levels deep,
 * in one request. Replace it with yours, or remove it.
 */
fun Coho.homepage(): EntryBase? = bySlug("home-page", ReadOptions(depth = 2))
