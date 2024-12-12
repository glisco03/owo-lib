package io.wispforest.owo;

import io.wispforest.owo.client.screens.ScreenInternals;
import io.wispforest.owo.command.debug.OwoDebugCommands;
import io.wispforest.owo.ops.LootOps;
import io.wispforest.owo.text.CustomTextRegistry;
import io.wispforest.owo.text.InsertingTextContent;
import io.wispforest.owo.util.Wisdom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

import static io.wispforest.owo.ops.TextOps.withColor;

public class Owo implements ModInitializer {

    /**
     * Whether oωo debug is enabled, this defaults to {@code true} in a development environment.
     * To override that behavior, add the {@code -Dowo.debug=false} java argument
     */
    public static final boolean DEBUG;
    public static final Logger LOGGER = LogManager.getLogger("owo");
    private static MinecraftServer SERVER;

    public static final Text PREFIX = Text.empty().formatted(Formatting.GRAY)
            .append(withColor("o", 0x3955e5))
            .append(withColor("ω", 0x13a6f0))
            .append(withColor("o", 0x3955e5))
            .append(Text.literal(" > ").formatted(Formatting.GRAY));

    static {
        boolean debug = FabricLoader.getInstance().isDevelopmentEnvironment();
        if (System.getProperty("owo.debug") != null) debug = Boolean.getBoolean("owo.debug");
        if (Boolean.getBoolean("owo.forceDisableDebug")) {
            LOGGER.warn("Deprecated system property 'owo.forceDisableDebug=true' was used - use 'owo.debug=false' instead");
            debug = false;
        }

        DEBUG = debug;
    }

    @Override
    @ApiStatus.Internal
    public void onInitialize() {
        LootOps.registerListener();
        CustomTextRegistry.register(InsertingTextContent.TYPE, "index");
        ScreenInternals.init();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> SERVER = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SERVER = null);

        if (!DEBUG) return;

        OwoDebugCommands.register();
    }

    @ApiStatus.Internal
    public static void debugWarn(Logger logger, String message) {
        if (!DEBUG) return;
        logger.warn(message);
    }

    @ApiStatus.Internal
    public static void debugWarn(Logger logger, String message, Object... params) {
        if (!DEBUG) return;
        logger.warn(message, params);
    }

    /**
     * @return The currently active minecraft server instance. If running
     * on a physical client, this will return the integrated server while in
     * a local singleplayer world and {@code null} otherwise
     */
    public static MinecraftServer currentServer() {
        return SERVER;
    }

}
