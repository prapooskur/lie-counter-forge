package com.johnlies.liecounter

import com.johnlies.commands.LieCommand
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.IExtensionPoint.DisplayTest
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.minecraftforge.network.NetworkConstants
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.forge.runForDist


/**
 * Main mod class. Should be an `object` declaration annotated with `@Mod`.
 * The modid should be declared in this object and should match the modId entry
 * in mods.toml.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(LieCounter.ID)
object LieCounter {

    const val ID = "liecounter"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {

        ModLoadingContext.get().registerExtensionPoint(
            DisplayTest::class.java
        ) {
            DisplayTest(
                { NetworkConstants.IGNORESERVERONLY },
                { a: String?, b: Boolean? -> true })
        }

        LOGGER.log(Level.INFO, "Hello world!")

        val obj = runForDist(
            clientTarget = {
                MOD_BUS.addListener(LieCounter::onClientSetup)
                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(LieCounter::onServerSetup)
                "test"
            }
        )

    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */

    // should never happen
    // if you do this, don't
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
        // register commands
        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(LieCommand)
    }


}