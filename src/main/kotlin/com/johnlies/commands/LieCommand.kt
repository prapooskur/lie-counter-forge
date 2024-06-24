package com.johnlies.commands

import com.johnlies.SUPABASE_KEY
import com.johnlies.SUPABASE_TABLE
import com.johnlies.SUPABASE_URL
import com.johnlies.liecounter.LieCounter
import com.johnlies.liecounter.LieCounter.LOGGER
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest


@Serializable
data class Liar(
    val uid: Long,
    val mc_uuid: String = "",
    val liecount: Int,
    val trustworthy: Boolean
)

// for lie top and pure
@Serializable
data class McPlayer(
    val id: String,
    val name: String,
)


@Mod.EventBusSubscriber(modid=LieCounter.ID, bus=Mod.EventBusSubscriber.Bus.FORGE)
object LieCommand {


//    val connection = DriverManager.getConnection(LieCounter.DB_URL, "postgres.xwatnpoacfkzeopqplfu", "Syrup7-Half-Entourage-Frosty")
//    val db_table = LieCounter.DB_TABLE

//    val supabase = createSupabaseClient(LieCounter.SUPABASE_URL, LieCounter.SUPABASE_KEY) {
//        install(Postgrest)
//    }

    val client: HttpClient = HttpClient.newBuilder().build()
    val json = Json { ignoreUnknownKeys = true }
    const val accent = "3"
    const val failAccent = "4"
    const val defaultName = "John Lies"

    @SubscribeEvent
    fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("lie")
                .then(Commands.literal("add").then(Commands.argument("user",EntityArgument.player()).executes { context: CommandContext<CommandSourceStack> ->
                    executeAdd(context, EntityArgument.getPlayer(context, "user"))
                }))
                .then(Commands.literal("count").then(Commands.argument("user",EntityArgument.player()).executes { context: CommandContext<CommandSourceStack> ->
                    executeCount(context, EntityArgument.getPlayer(context, "user"))
                }))
                .then(Commands.literal("set").then(Commands.argument("user",EntityArgument.player()).then(Commands.argument("count",IntegerArgumentType.integer()).executes { context: CommandContext<CommandSourceStack> ->
                    executeSet(context, EntityArgument.getPlayer(context, "user"), IntegerArgumentType.getInteger(context, "count"))
                })))
                .then(Commands.literal("sync").then(Commands.argument("user",EntityArgument.player()).then(Commands.argument("discord",StringArgumentType.word()).executes { context: CommandContext<CommandSourceStack> ->
                    executeSync(context, EntityArgument.getPlayer(context, "user"), StringArgumentType.getString(context, "discord"))
                })))
                .then(Commands.literal("top").executes { context: CommandContext<CommandSourceStack> ->
                    executeTop(context)
                })
                .then(Commands.literal("pure").executes { context: CommandContext<CommandSourceStack> ->
                    executePure(context)
                })
        )
    }

    private fun executeAdd(context: CommandContext<CommandSourceStack>, target: ServerPlayer): Int {
        // add lie
        val message: String
        val hoverText = "§${accent}${context.source.player?.displayName?.string ?: defaultName}§r: /lie add ${target.displayName.string}"
        LOGGER.info(hoverText)

        try {
            val init_request = createRequest("mc_uuid=eq.${target.stringUUID}", Method.GET)
            val init_response = client.send(init_request, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (init_response.statusCode() < 200 || init_response.statusCode() > 299) {
                // caught by try-catching the response
                throw Exception(init_response.body())
            }
            val liar = json.decodeFromString<List<Liar>>(init_response.body()).firstOrNull()
            if (liar == null) {
                throw Exception("User does not exist. Please run /lie sync.")
            }

            val body = """{"liecount": "${liar.liecount + 1}"}"""
            val request = createRequest("mc_uuid=eq.${target.stringUUID}", Method.PATCH, body)
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                // caught by try-catching the response
                throw Exception(response.body())
            }

            message = "§${accent}${target.name.string}§r has lied §${accent}${liar.liecount+1}§r times."

        } catch (e: Exception) {
            val errMessage = "Error adding lie to ${target.name.string}: ${e.message}"
            println(errMessage)
            context.source.sendFailure(Component.literal(errMessage))
            return Command.SINGLE_SUCCESS
        }
        println(message)
//        context.source.sendSuccess({
//            Component.literal(message).withStyle { style ->
//                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
//            }
//        }, true)
        val playerList = context.source.server.playerList
        playerList.broadcastSystemMessage(
            Component.literal(message).withStyle { style ->
                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
            },
            false
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeCount(context: CommandContext<CommandSourceStack>, target: ServerPlayer): Int {
        // count lies
        val message: String
        val hoverText = "§${accent}${context.source.player?.displayName?.string ?: defaultName}§r: /lie count ${target.displayName.string}"
        LOGGER.info(hoverText)

        try {
            val request = createRequest("mc_uuid=eq.${target.stringUUID}", Method.GET)
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                // caught by try-catching the response
                throw Exception(response.body())
            }
            val liar = json.decodeFromString<List<Liar>>(response.body()).firstOrNull()
            if (liar == null) {
//                message = "${target.name.string} does not exist. Please run /lie sync."
                throw Exception("User does not exist. Please run /lie sync.")
            } else if (liar.liecount == 0) {
                message = "§${accent}${target.name.string}§r has never been caught in a lie. §${accent}Congratulations!§r"
            } else {
                message = "§${accent}${target.name.string}§r has lied §${accent}${liar.liecount}§r times."
            }
        } catch (e: Exception) {
            val errMessage = "Error counting lies for ${target.stringUUID}: ${e.message}"
            println(errMessage)
            context.source.sendFailure(Component.literal(errMessage))
            return Command.SINGLE_SUCCESS
        }
        println(message)
//        context.source.sendSuccess({
//            Component.literal(message).withStyle { style ->
//                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
//            }
//        }, true)
        val playerList = context.source.server.playerList
        playerList.broadcastSystemMessage(
            Component.literal(message).withStyle { style ->
                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
            },
            false
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeSet(context: CommandContext<CommandSourceStack>, target: ServerPlayer, newCount: Int): Int {
        // set lie
        // check for user trust

        val message: String
        val caller: Liar?
        val hoverText = "§${accent}${context.source.player?.displayName?.string ?: defaultName}§r: /lie set ${target.stringUUID} $newCount"
        LOGGER.info(hoverText)

        try {
            if (context.source.player?.stringUUID == null) {
                caller = null
            } else {
                val request = createRequest("mc_uuid=eq.${context.source.player?.stringUUID}", Method.GET)
                val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() < 200 || response.statusCode() > 299) {
                    // caught by try-catching the response
                    throw Exception(response.body())
                }
                caller = json.decodeFromString<List<Liar>>(response.body()).firstOrNull()
            }
        } catch (e: Exception) {
            val errMessage = "Set: Error checking trust for ${target.stringUUID}: ${e.message}"
            println(errMessage)
            context.source.sendFailure(Component.literal(errMessage))
            return Command.SINGLE_SUCCESS
        }

        // check if caller is to be trusted
        if (caller == null || !caller.trustworthy) {
            val user = context.source.player?.displayName?.string ?: "whoever you are"
            message = "§${failAccent}I'm afraid I can't do that, §$accent$user§${failAccent}.§r"
            println("User ${user} failed to set lies for ${target} to ${newCount}. This incident has been recorded.")
            context.source.sendSuccess({ Component.literal(message) }, true)
            return Command.SINGLE_SUCCESS
        }

        try {
            val body = """{"liecount": "$newCount"}"""
            val request = createRequest("mc_uuid=eq.${target.stringUUID}", Method.PATCH, body)
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                // caught by try-catching the response
                throw Exception("${response.statusCode()}: ${response.body()}")
            }
            println(response.body())
//            val liar = json.decodeFromString<List<Liar>>(response.body()).firstOrNull()
//            if (liar == null) {
//                throw Exception("User does not exist. Please run /lie sync.")
//            }
            message = "Set §${accent}${target.name.string}§r's lies to §${accent}$newCount§r. Your sins have been absolved §${accent}(for now)§r."
        } catch (e: Exception) {
            val errMessage = "Error setting lies for ${target.stringUUID} to ${newCount}: ${e.message}"
            println(errMessage)
            context.source.sendFailure(Component.literal(errMessage))
            return Command.SINGLE_SUCCESS
        }
        println(message)
//        context.source.sendSuccess({
//            Component.literal(message).withStyle { style ->
//                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
//            }
//        }, true)
        val playerList = context.source.server.playerList
        playerList.broadcastSystemMessage(
            Component.literal(message).withStyle { style ->
                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
            },
            false
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeSync(context: CommandContext<CommandSourceStack>, target: ServerPlayer, discord_uuid: String): Int {
        // sync minecraft and discord users

        // check for user trust
        val message: String
        val caller: Liar?

        val hoverText = "§${accent}${context.source.player?.displayName?.string ?: defaultName}§r: /lie sync ${target.stringUUID} $discord_uuid"
        LOGGER.info(hoverText)

        try {
            if (context.source.player?.stringUUID == null) {
                caller = null
            } else {
                val request = createRequest("mc_uuid=eq.${context.source.player?.stringUUID}", Method.GET)
                val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() < 200 || response.statusCode() > 299) {
                    // caught by try-catching the response
                    throw Exception(response.body())
                }
                caller = json.decodeFromString<List<Liar>>(response.body()).firstOrNull()
            }
        } catch (e: Exception) {
            val errMessage = "Sync: Error checking trust for ${target.stringUUID}: ${e.message}"
            println(errMessage)
            context.source.sendFailure(Component.literal(errMessage))
            return Command.SINGLE_SUCCESS
        }

        // check if caller is to be trusted
        if (caller == null || !caller.trustworthy) {
            val user = context.source.player?.displayName?.string ?: "whoever you are"
            message = "§${failAccent}I'm afraid I can't let you do that, §$accent$user§${failAccent}.§r"
            println("User ${context.source.player?.stringUUID} failed to sync lies for ${target.name.string} to ${discord_uuid}.")
            // context.source.sendSuccess({ Component.literal(message) }, true)
            val playerList = context.source.server.playerList
            playerList.broadcastSystemMessage(Component.literal(message), false)
            return Command.SINGLE_SUCCESS
        }

        try {
            val body = """{"mc_uuid": "${target.stringUUID}"}"""
            val request = createRequest("uid=eq.${discord_uuid}", Method.PATCH, body)
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            println("${response.statusCode()}: ${response.body()}")
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                // caught by try-catching the response
                throw Exception("${response.statusCode()}: ${response.body()}")
            }

            message = "Synced lies of §${accent}${target.name.string}§r to §${accent}${discord_uuid}§r. Congratulations!"
        } catch (e: Exception) {
            val errMessage = "Error syncing lies of ${target.stringUUID} to ${discord_uuid}: ${e.message}"
            println(errMessage)
            context.source.sendFailure(Component.literal(errMessage))
            return Command.SINGLE_SUCCESS
        }

        println(message)
//        context.source.sendSuccess({
//            Component.literal(message).withStyle { style ->
//                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
//            }
//        }, true)
        val playerList = context.source.server.playerList
        playerList.broadcastSystemMessage(
            Component.literal(message).withStyle { style ->
                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
            },
            false
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeTop(context: CommandContext<CommandSourceStack>): Int {
        // only show synced users
        val message: String
        val hoverText = "§${accent}${context.source.player?.displayName?.string ?: defaultName}§r: /lie top"
        LOGGER.info(hoverText)

        try {
            val request = createRequest("mc_uuid=not.is.null&order=liecount.desc&limit=10", Method.GET)
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            println("${response.statusCode()}: ${response.body()}")
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                // caught by try-catching the response
                throw Exception("${response.statusCode()}: ${response.body()}")
            }

            val liars = json.decodeFromString<List<Liar>>(response.body())



            // Generate printable table string
            val tableHeader = String.format("%-9s | %-10s\n", "Name", "Lies")
            val tableDivider = "-".repeat(25)
            val tableRows = liars.joinToString("\n") { liar ->
                val uuidRequest = createUUIDRequest(liar.mc_uuid)
                val player = json.decodeFromString<McPlayer>(client.send(uuidRequest, java.net.http.HttpResponse.BodyHandlers.ofString()).body())
                String.format("%-10s | %-10d", "§${accent}${player.name}§r", liar.liecount)
            }

            message = "$tableHeader$tableDivider\n$tableRows"
        } catch (e: Exception) {
            val errMessage = "Error getting top liars: ${e.message}"
            println(errMessage)
            context.source.sendFailure(Component.literal(errMessage))
            return Command.SINGLE_SUCCESS
        }
        println("Top liars printed.")
//        context.source.sendSuccess({
//            Component.literal(message).withStyle { style ->
//                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
//            }
//        }, true)
        val playerList = context.source.server.playerList
        playerList.broadcastSystemMessage(
            Component.literal(message).withStyle { style ->
                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
            },
            false
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executePure(context: CommandContext<CommandSourceStack>): Int {
        // only show synced users
        // only show synced users
        val message: String
        val hoverText = "§${accent}${context.source.player?.displayName?.string ?: defaultName}§r: /lie pure"
        LOGGER.info(hoverText)

        try {
            val request = createRequest("mc_uuid=not.is.null&order=liecount.asc&limit=10", Method.GET)
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            println("${response.statusCode()}: ${response.body()}")
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                // caught by try-catching the response
                throw Exception("${response.statusCode()}: ${response.body()}")
            }

            val liars = json.decodeFromString<List<Liar>>(response.body())



            // Generate printable table string
            val tableHeader = String.format("%-9s | %-10s\n", "Name", "Lies")
            val tableDivider = "-".repeat(25)
            val tableRows = liars.joinToString("\n") { liar ->
                val uuidRequest = createUUIDRequest(liar.mc_uuid)
                val uuidResponse = client.send(uuidRequest, java.net.http.HttpResponse.BodyHandlers.ofString())
                println(uuidResponse.body())
                val player = json.decodeFromString<McPlayer>(uuidResponse.body())
                String.format("%-10s | %-10d", "§${accent}${player.name}§r", liar.liecount)
            }

            message = "$tableHeader$tableDivider\n$tableRows"
        } catch (e: Exception) {
            val errMessage = "Error getting top liars: ${e.message}"
            println(errMessage)
            context.source.sendFailure(Component.literal(errMessage))
            return Command.SINGLE_SUCCESS
        }
        println("Top liars printed.")
//        context.source.sendSuccess({
//            Component.literal(message).withStyle { style ->
//                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
//            }
//        }, true)
        val playerList = context.source.server.playerList
        playerList.broadcastSystemMessage(
            Component.literal(message).withStyle { style ->
                style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
            },
            false
        )
        return Command.SINGLE_SUCCESS
    }


    enum class Method {
        GET,
        POST,
        PUT,
        PATCH
    }

    private fun createRequest(payload: String, method: Method, json: String = ""): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("${SUPABASE_URL}/rest/v1/${SUPABASE_TABLE}?$payload"))
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .header("Content-Type", "application/json")
        if (method != Method.GET && json.isEmpty()) {
            throw Exception("null/empty json with method $method is not allowed.")
        }
        when (method) {
            Method.GET -> builder.GET()
            Method.POST -> builder.POST(HttpRequest.BodyPublishers.ofString(json))
            Method.PATCH -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(json))
            Method.PUT -> builder.PUT(HttpRequest.BodyPublishers.ofString(json))
        }
        return builder.build()
    }

    private fun createUUIDRequest(uuid: String): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$uuid"))
        return builder.build()
    }

}