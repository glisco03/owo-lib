package io.wispforest.uwu.items;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import io.wispforest.owo.itemgroup.OwoItemGroup;
import io.wispforest.owo.itemgroup.OwoItemSettings;
import io.wispforest.owo.nbt.NbtKey;
import io.wispforest.owo.ops.WorldOps;
import io.wispforest.uwu.Uwu;
import io.wispforest.uwu.text.BasedTextContent;
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class UwuTestStickItem extends Item {
    private static final NbtKey<Text> TEXT_KEY = new NbtKey<>("Text", NbtKey.Type.STRING.then(Text.Serializer::fromJson, Text.Serializer::toJson));
    private static final NbtKey<Set<Text>> TEXTS_KEY = new NbtKey<>("Texts", NbtKey.Type.collectionType(NbtKey.Type.STRING.then(Text.Serializer::fromJson, Text.Serializer::toJson), HashSet::new));

    private static final NbtKey<Map<String, Integer>> MAP_NBT_KEY = new NbtKey<>("CursedMap", NbtKey.Type.mapType(NbtKey.Type.STRING, NbtKey.Type.INT, HashMap::new));

    public UwuTestStickItem() {
        super(new OwoItemSettings().group(Uwu.SIX_TAB_GROUP).tab(3).maxCount(1)
                .stackGenerator(OwoItemGroup.DEFAULT_STACK_GENERATOR.andThen((item, stacks) -> {
                    final var stack = new ItemStack(item);
                    stack.setCustomName(Text.literal("the stick of the test").styled(style -> style.withItalic(false)));
                    stacks.add(stack);
                })));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user.isSneaking()) {
            if (world.isClient) return TypedActionResult.success(user.getStackInHand(hand));

            Uwu.CHANNEL.serverHandle(user).send(new Uwu.OtherTestMessage(user.getBlockPos(), "based"));

            var server = user.getServer();
            var teleportTo = world.getRegistryKey() == World.END ? server.getWorld(World.OVERWORLD) : server.getWorld(World.END);

            WorldOps.teleportToWorld((ServerPlayerEntity) user, teleportTo, new Vec3d(0, 128, 0));

            return TypedActionResult.success(user.getStackInHand(hand));
        } else {
            if (!world.isClient) return TypedActionResult.success(user.getStackInHand(hand));

            Uwu.CHANNEL.clientHandle().send(Uwu.MESSAGE);

            Uwu.CUBE.spawn(world, user.getEyePos().add(user.getRotationVec(0).multiply(3)).subtract(.5, .5, .5), null);

            return TypedActionResult.success(user.getStackInHand(hand));
        }
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getPlayer().isSneaking()) return ActionResult.PASS;
        if (context.getWorld().isClient) return ActionResult.SUCCESS;

        final var breakStack = new ItemStack(Items.NETHERITE_PICKAXE);
        breakStack.addEnchantment(Enchantments.FORTUNE, 3);
        WorldOps.breakBlockWithItem(context.getWorld(), context.getBlockPos(), breakStack);

        final var stickStack = context.getStack();

        if (!stickStack.has(TEXT_KEY)) {
            stickStack.put(TEXT_KEY, Text.of(String.valueOf(context.getWorld().random.nextInt(1000000))));
        }

        stickStack.mutate(TEXT_KEY, text -> MutableText.of(new BasedTextContent("basednite, ")).append(text));

        context.getPlayer().sendMessage(stickStack.get(TEXT_KEY), false);

        //--

        if (!stickStack.has(TEXTS_KEY)) {
            stickStack.put(TEXTS_KEY, Set.of(
                    Text.of(String.valueOf(context.getWorld().random.nextInt(1000000))),
                    Text.of(String.valueOf(context.getWorld().random.nextInt(1000000))),
                    Text.of(String.valueOf(context.getWorld().random.nextInt(1000000))),
                    Text.of(String.valueOf(context.getWorld().random.nextInt(1000000)))
            ));
        }

        stickStack.mutate(TEXTS_KEY, texts -> {
            return texts.stream()
                    .map(text -> MutableText.of(new BasedTextContent("basednite, ")).append(text))
                    .collect(Collectors.toSet());
        });

        context.getPlayer().sendMessage(stickStack.get(TEXTS_KEY).stream().reduce(Text.empty(), (mutableText, text) -> text.copy(), MutableText::append), false);

        //--

        Uwu.BREAK_BLOCK_PARTICLES.spawn(context.getWorld(), Vec3d.of(context.getBlockPos()), null);

        return ActionResult.SUCCESS;
    }
}
