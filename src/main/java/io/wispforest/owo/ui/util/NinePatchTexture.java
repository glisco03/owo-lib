package io.wispforest.owo.ui.util;

import io.wispforest.endec.Endec;
import io.wispforest.endec.StructEndec;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.owo.serialization.CodecUtils;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.PositionedRectangle;
import io.wispforest.owo.ui.core.Size;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class NinePatchTexture {

    private final Identifier texture;
    private final int u, v;
    private final PatchSizing patchSizing;
    private final Size textureSize;
    private final boolean repeat;

    public NinePatchTexture(Identifier texture, int u, int v, PatchSizing patchSizing, Size textureSize, boolean repeat) {
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.textureSize = textureSize;
        this.patchSizing = patchSizing;
        this.repeat = repeat;
    }

    public NinePatchTexture(Identifier texture, int u, int v, Size cornerPatchSize, Size centerPatchSize, Size textureSize, boolean repeat) {
        this(texture, u, v, new PatchSizing(null, cornerPatchSize, centerPatchSize), textureSize, repeat);
    }

    public NinePatchTexture(Identifier texture, int u, int v, Size patchSize, Size textureSize, boolean repeat) {
        this(texture, u, v, new PatchSizing(patchSize, null, null), textureSize, repeat);
    }

    private Size cornerPatchSize() {
        return this.patchSizing.cornerPatchSize();
    }

    private Size centerPatchSize() {
        return this.patchSizing.centerPatchSize();
    }

    public void draw(OwoUIDrawContext context, PositionedRectangle rectangle) {
        this.draw(context, rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height());
    }

    public void draw(OwoUIDrawContext context, int x, int y, int width, int height) {
        draw(context, RenderLayer::getGuiTextured, x, y, width, height);
    }

    public void draw(OwoUIDrawContext context, Function<Identifier, RenderLayer> renderLayers, int x, int y, int width, int height) {
        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        context.drawTexture(renderLayers, this.texture, x, y, this.u, this.v, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height());
        context.drawTexture(renderLayers, this.texture, x + width - this.cornerPatchSize().width(), y, this.u + rightEdge, this.v, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height());
        context.drawTexture(renderLayers, this.texture, x, y + height - this.cornerPatchSize().height(), this.u, this.v + bottomEdge, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height());
        context.drawTexture(renderLayers, this.texture, x + width - this.cornerPatchSize().width(), y + height - this.cornerPatchSize().height(), this.u + rightEdge, this.v + bottomEdge, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height());

        if (this.repeat) {
            this.drawRepeated(context, renderLayers, x, y, width, height);
        } else {
            this.drawStretched(context, renderLayers, x, y, width, height);
        }
    }

    protected void drawStretched(OwoUIDrawContext context, Function<Identifier, RenderLayer> renderLayers, int x, int y, int width, int height) {
        int doubleCornerHeight = this.cornerPatchSize().height() * 2;
        int doubleCornerWidth = this.cornerPatchSize().width() * 2;

        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        if (width > doubleCornerWidth && height > doubleCornerHeight) {
            context.drawTexture(renderLayers, this.texture, x + this.cornerPatchSize().width(), y + this.cornerPatchSize().height(),
                    this.u + this.cornerPatchSize().width(), this.v + this.cornerPatchSize().height(),
                    width - doubleCornerWidth, height - doubleCornerHeight,
                    this.centerPatchSize().width(), this.centerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height());
        }

        if (width > doubleCornerWidth) {
            context.drawTexture(renderLayers, this.texture, x + this.cornerPatchSize().width(), y,
                    this.u + this.cornerPatchSize().width(), this.v,
                    width - doubleCornerWidth, this.cornerPatchSize().height(),
                    this.centerPatchSize().width(), this.cornerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height());
            context.drawTexture(renderLayers, this.texture, x + this.cornerPatchSize().width(), y + height - this.cornerPatchSize().height(),
                    this.u + this.cornerPatchSize().width(), this.v + bottomEdge,
                    width - doubleCornerWidth, this.cornerPatchSize().height(),
                    this.centerPatchSize().width(), this.cornerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height());
        }

        if (height > doubleCornerHeight) {
            context.drawTexture(renderLayers, this.texture, x, y + this.cornerPatchSize().height(),
                    this.u, this.v + this.cornerPatchSize().height(),
                    this.cornerPatchSize().width(), height - doubleCornerHeight,
                    this.cornerPatchSize().width(), this.centerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height());
            context.drawTexture(renderLayers, this.texture, x + width - this.cornerPatchSize().width(), y + this.cornerPatchSize().height(),
                    this.u + rightEdge, this.v + this.cornerPatchSize().height(),
                    this.cornerPatchSize().width(), height - doubleCornerHeight,
                    this.cornerPatchSize().width(), this.centerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height());
        }
    }

    protected void drawRepeated(OwoUIDrawContext context, Function<Identifier, RenderLayer> renderLayers, int x, int y, int width, int height) {
        int doubleCornerHeight = this.cornerPatchSize().height() * 2;
        int doubleCornerWidth = this.cornerPatchSize().width() * 2;

        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        if (width > doubleCornerWidth && height > doubleCornerHeight) {
            int leftoverHeight = height - doubleCornerHeight;
            while (leftoverHeight > 0) {
                int drawHeight = Math.min(this.centerPatchSize().height(), leftoverHeight);

                int leftoverWidth = width - doubleCornerWidth;
                while (leftoverWidth > 0) {
                    int drawWidth = Math.min(this.centerPatchSize().width(), leftoverWidth);
                    context.drawTexture(renderLayers, this.texture,
                            x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                            this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                            drawWidth, drawHeight,
                            drawWidth, drawHeight,
                            this.textureSize.width(), this.textureSize.height());

                    leftoverWidth -= this.centerPatchSize().width();
                }
                leftoverHeight -= this.centerPatchSize().height();
            }
        }

        if (width > doubleCornerWidth) {
            int leftoverWidth = width - doubleCornerWidth;
            while (leftoverWidth > 0) {
                int drawWidth = Math.min(this.centerPatchSize().width(), leftoverWidth);

                context.drawTexture(renderLayers, this.texture, x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y,
                        this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v,
                        drawWidth, this.cornerPatchSize().height(),
                        drawWidth, this.cornerPatchSize().height(),
                        this.textureSize.width(), this.textureSize.height());
                context.drawTexture(renderLayers, this.texture, x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y + height - this.cornerPatchSize().height(),
                        this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v + bottomEdge,
                        drawWidth, this.cornerPatchSize().height(),
                        drawWidth, this.cornerPatchSize().height(),
                        this.textureSize.width(), this.textureSize.height());

                leftoverWidth -= this.centerPatchSize().width();
            }
        }

        if (height > doubleCornerHeight) {
            int leftoverHeight = height - doubleCornerHeight;
            while (leftoverHeight > 0) {
                int drawHeight = Math.min(this.centerPatchSize().height(), leftoverHeight);
                context.drawTexture(renderLayers, this.texture, x, y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                        this.u, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                        this.cornerPatchSize().width(), drawHeight,
                        this.cornerPatchSize().width(), drawHeight,
                        this.textureSize.width(), this.textureSize.height());
                context.drawTexture(renderLayers, this.texture, x + width - this.cornerPatchSize().width(), y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                        this.u + rightEdge, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                        this.cornerPatchSize().width(), drawHeight,
                        this.cornerPatchSize().width(), drawHeight,
                        this.textureSize.width(), this.textureSize.height());

                leftoverHeight -= this.centerPatchSize().height();
            }
        }
    }

    public static void draw(Identifier texture, OwoUIDrawContext context, int x, int y, int width, int height) {
        draw(texture, context, RenderLayer::getGuiTextured, x, y, width, height);
    }

    public static void draw(Identifier texture, OwoUIDrawContext context, Function<Identifier, RenderLayer> renderLayers, int x, int y, int width, int height) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, renderLayers, x, y, width, height));
    }

    public static void draw(Identifier texture, OwoUIDrawContext context, PositionedRectangle rectangle) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, rectangle));
    }

    private static void ifPresent(Identifier texture, Consumer<NinePatchTexture> action) {
        if (!MetadataLoader.LOADED_TEXTURES.containsKey(texture)) return;
        action.accept(MetadataLoader.LOADED_TEXTURES.get(texture));
    }

    public static final Endec<NinePatchTexture> ENDEC = StructEndecBuilder.of(
            MinecraftEndecs.IDENTIFIER.fieldOf("texture", (texture) -> texture.texture),
            Endec.INT.optionalFieldOf("u", (texture) -> texture.u, 0),
            Endec.INT.optionalFieldOf("v", (texture) -> texture.v, 0),
            PatchSizing.ENDEC.flatFieldOf((texture) -> texture.patchSizing),
            StructEndecBuilder.of(
                    Endec.INT.fieldOf("texture_width", Size::width),
                    Endec.INT.fieldOf("texture_height", Size::height),
                    Size::of
            ).flatFieldOf((texture) -> texture.textureSize),
            Endec.BOOLEAN.fieldOf("repeat", (texture) -> texture.repeat),
            NinePatchTexture::new
    );

    private record PatchSizing(@Nullable Size patchSize, @Nullable Size cornerPatchSize, @Nullable Size centerPatchSize) {
        public static final StructEndec<PatchSizing> ENDEC = StructEndecBuilder.of(
                Size.ENDEC.nullableOf().optionalFieldOf("patch_size", PatchSizing::patchSize, () -> null),
                Size.ENDEC.nullableOf().optionalFieldOf("corner_patch_size", PatchSizing::cornerPatchSize, () -> null),
                Size.ENDEC.nullableOf().optionalFieldOf("center_patch_size", PatchSizing::centerPatchSize, () -> null),
                PatchSizing::new
        );

        public PatchSizing {
            if (patchSize == null) {
                if ((cornerPatchSize != null && centerPatchSize == null)) {
                    throw new IllegalStateException("Missing center Patch Size while providing corner Patch Size!");
                } else if ((cornerPatchSize == null && centerPatchSize != null)) {
                    throw new IllegalStateException("Missing corner Patch Size while providing center Patch Size!");
                } else if ((cornerPatchSize == null && centerPatchSize == null)) {
                    throw new IllegalStateException("Missing base patch Size or patch size for both corner and center!");
                }
            }
        }

        @NotNull
        @Override
        public Size cornerPatchSize() {
            return (this.cornerPatchSize != null) ? this.cornerPatchSize : this.patchSize;
        }

        @NotNull
        @Override
        public Size centerPatchSize() {
            return (this.centerPatchSize != null) ? this.centerPatchSize : this.patchSize;
        }
    }

    public static class MetadataLoader extends JsonDataLoader<NinePatchTexture> implements IdentifiableResourceReloadListener {

        private static final Map<Identifier, NinePatchTexture> LOADED_TEXTURES = new HashMap<>();

        public MetadataLoader() {
            super(CodecUtils.toCodec(NinePatchTexture.ENDEC), ResourceFinder.json("nine_patch_textures"));
        }

        @Override
        public Identifier getFabricId() {
            return Identifier.of("owo", "nine_patch_metadata");
        }

        protected void apply(Map<Identifier, NinePatchTexture> prepared, ResourceManager manager, Profiler profiler) {
            LOADED_TEXTURES.putAll(prepared);
        }
    }

}
