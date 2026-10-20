// `./gradlew catalog`: every content type, and how many entries each has, read the way an app
// reads them.
//
// Released content by default, from delivery, with COHO_DELIVERY_URL and COHO_DELIVERY_KEY. The
// ref is `--ref`, or COHO_REF, or the ref in coho.config.json.
//
// `--preview` reads unreleased content instead, live through Coho, with the COHO_TOKEN this
// library pulls its types with, or COHO_PREVIEW_TOKEN if that is set. The ref is `--ref`, or `dev`.
//
// The project, and for a preview Coho's URL and the account, come from coho.config.json unless
// the environment or `.env` sets them, under the names an app's configuration uses.
//
// A development tool: it is in a source set of its own, so it is never in the library.
package com.yourorg.content.tools

import com.cohocms.delivery.ClientOptions
import com.cohocms.delivery.CohoDeliveryException
import com.cohocms.delivery.CohoEnvironment
import com.cohocms.delivery.ContentConfig
import com.cohocms.delivery.DeliveryClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.yourorg.content.coho.Coho
import java.io.File
import kotlin.system.exitProcess

/** How many entries of its own one content type has. */
data class Counted(val type: String, val count: Int)

/**
 * Every content type this library was generated for, with how many entries of its own it has on
 * the config's ref. Each is counted by paging through its list, a hundred entries a request; a
 * type's list holds the types that extend it too, so only its own are counted. It is for looking
 * around, not for every page view.
 */
fun count(config: ContentConfig, options: ClientOptions = ClientOptions()): List<Counted>
{
    val delivery = DeliveryClient(config.delivery, options.withCacheSize(0))
    return Coho.CONTENT_TYPES.sorted().map()
    { type ->
        var own = 0
        var offset = 0
        while (true)
        {
            val page = delivery.entries(config.ref, type = type, limit = 100, offset = offset)
            own += page.roots.count { page.entries[it]?.type == type }
            offset += page.roots.size
            if (!page.hasMore || page.roots.isEmpty())
            {
                break
            }
        }
        Counted(type, own)
    }
}

fun main(args: Array<String>)
{
    val preview = "--preview" in args
    val askedRef = args.indexOf("--ref").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }

    val json = ObjectMapper()
    val configured = json.readTree(File("coho.config.json"))

    // The variables an app would set: the environment, then .env, with gaps filled from coho.config.json.
    val env = HashMap(dotenv(File(".env")))
    env.putAll(System.getenv())
    fun fill(name: String, value: String?)
    {
        if (env[name].isNullOrBlank() && value != null)
        {
            env[name] = value
        }
    }
    fill("COHO_PROJECT", configured.path("project").asText(null))
    if (preview)
    {
        fill("COHO_PREVIEW_TOKEN", env["COHO_TOKEN"])
        if (env["COHO_PREVIEW_TOKEN"].isNullOrBlank())
        {
            System.err.println("The catalog's --preview needs COHO_TOKEN in .env, a project token with read access. See .env.example.")
            exitProcess(1)
        }
        fill("COHO_URL", configured.path("url").asText(null))
        fill("COHO_ACCOUNT", configured.path("account").asText(null))
        env.remove("COHO_DELIVERY_KEY")
        // Not the config's ref: that is a tag, and a preview reads branches.
        env["COHO_REF"] = askedRef ?: "dev"
    }
    else
    {
        env.remove("COHO_PREVIEW_TOKEN")
        fill("COHO_REF", configured.path("ref").asText(null))
        askedRef?.let { env["COHO_REF"] = it }
    }

    val config = try
    {
        CohoEnvironment.from { env[it] }
    }
    catch (error: IllegalStateException)
    {
        System.err.println("The catalog: ${error.message}. See .env.example.")
        exitProcess(1)
    }

    // Display names, from the type model the code was generated from.
    val model = json.readTree(File(configured.path("typeModel").asText("coho-ir.json")))
    val counted = try
    {
        count(config)
    }
    catch (error: CohoDeliveryException)
    {
        // Coho's own words, such as KEY_REQUIRED or PROJECT_NOT_FOUND, and what to check.
        System.err.println(error.message)
        exitProcess(1)
    }

    val names = counted.associate { it.type to model.path("types").path(it.type).path("name").asText("") }
    val typeWidth = counted.maxOfOrNull { it.type.length } ?: 0
    val nameWidth = names.values.maxOfOrNull { it.length } ?: 0
    val total = counted.sumOf { it.count }
    println("${if (preview) "Unreleased content" else "Content"} on ${config.ref}:\n")
    counted.forEach { println("  ${it.type.padEnd(typeWidth)}   ${names.getValue(it.type).padEnd(nameWidth)}   ${it.count.toString().padStart(5)}") }
    println("\n  $total ${if (total == 1) "entry" else "entries"} in ${counted.size} ${if (counted.size == 1) "type" else "types"}")
}

private fun dotenv(file: File): Map<String, String> =
    if (!file.exists())
    {
        emptyMap()
    }
    else
    {
        file.readLines()
            .filter { '=' in it && !it.trimStart().startsWith('#') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().removeSurrounding("\"") }
    }
