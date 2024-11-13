package io.wispforest.owo.client;

import io.wispforest.owo.Owo;
import io.wispforest.owo.client.screens.ScreenInternals;
import io.wispforest.owo.command.debug.OwoDebugCommands;
import io.wispforest.owo.config.OwoConfigCommand;
import io.wispforest.owo.config.ui.ConfigScreenProviders;
import io.wispforest.owo.itemgroup.json.OwoItemGroupLoader;
import io.wispforest.owo.moddata.ModDataLoader;
import io.wispforest.owo.shader.BlurProgram;
import io.wispforest.owo.shader.GlProgram;
import io.wispforest.owo.ui.parsing.UIModelLoader;
import io.wispforest.owo.ui.util.NinePatchTexture;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@OnlyIn(Dist.CLIENT)
@Mod(value = "owo", dist = Dist.CLIENT)
public class OwoClient {

    private static final String LINUX_RENDERDOC_WARNING = """

            ========================================
            Ignored 'owo.renderdocPath' property as this Minecraft instance is not running on Windows.
            Please populate the LD_PRELOAD environment variable instead
            ========================================""";

    private static final String MAC_RENDERDOC_WARNING = """

            ========================================
            Ignored 'owo.renderdocPath' property as this Minecraft instance is not running on Windows.
            RenderDoc is not supported on macOS
            ========================================""";

    private static final String GENERIC_RENDERDOC_WARNING = """

            ========================================
            Ignored 'owo.renderdocPath' property as this Minecraft instance is not running on Windows.
            ========================================""";

    public static final GlProgram HSV_PROGRAM = new GlProgram(Identifier.of("owo", "spectrum"), VertexFormats.POSITION_COLOR);
    public static final BlurProgram BLUR_PROGRAM = new BlurProgram();

    public OwoClient(IEventBus modBus) {
        ModDataLoader.load(OwoItemGroupLoader.INSTANCE);
        OwoItemGroupLoader.initItemGroupCallback();

        modBus.addListener((RegisterClientReloadListenersEvent event) -> {
            event.registerReloadListener(new UIModelLoader());
            event.registerReloadListener(new NinePatchTexture.MetadataLoader());
        });

        final var renderdocPath = System.getProperty("owo.renderdocPath");
        if (renderdocPath != null) {
            if (Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS) {
                System.load(renderdocPath);
            } else {
                Owo.LOGGER.warn(switch (Util.getOperatingSystem()) {
                    case LINUX -> LINUX_RENDERDOC_WARNING;
                    case OSX -> MAC_RENDERDOC_WARNING;
                    default -> GENERIC_RENDERDOC_WARNING;
                });
            }
        }

        ScreenInternals.Client.init();

        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> {
            OwoConfigCommand.register(event.getDispatcher(), event.getBuildContext());
        });

        if (Owo.DEBUG) {
            OwoDebugCommands.Client.register();
        }

        modBus.addListener(FMLClientSetupEvent.class, event -> {
            ConfigScreenProviders.forEach((modId, screenFactory) -> {
                ModList.get().getModContainerById(modId)
                        .ifPresent(mod -> mod.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, modsScreen) -> screenFactory.apply(modsScreen)));
            });
        });
    }
}
