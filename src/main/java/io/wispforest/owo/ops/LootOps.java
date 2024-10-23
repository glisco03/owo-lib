package io.wispforest.owo.ops;

import io.wispforest.owo.mixin.SetComponentsLootFunctionAccessor;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A simple utility class to make injecting simple items or
 * ItemStacks into one or multiple LootTables a one-line operation
 */
public final class LootOps {

    private LootOps() {}

    private static final Map<Identifier[], Supplier<LootPoolEntry.Builder<?>>> ADDITIONS = new HashMap<>();

    /**
     * Injects a single item entry into the specified LootTable(s)
     *
     * @param item         The item to inject
     * @param chance       The chance for the item to actually generate
     * @param targetTables The LootTable(s) to inject into
     */
    public static void injectItem(ItemConvertible item, float chance, Identifier... targetTables) {
        ADDITIONS.put(targetTables, () -> ItemEntry.builder(item).conditionally(RandomChanceLootCondition.builder(chance)));
    }

    /**
     * Injects an item entry into the specified LootTable(s),
     * with a random count between {@code min} and {@code max}
     *
     * @param item         The item to inject
     * @param chance       The chance for the item to actually generate
     * @param min          The minimum amount of items to generate
     * @param max          The maximum amount of items to generate
     * @param targetTables The LootTable(s) to inject into
     */
    public static void injectItemWithCount(ItemConvertible item, float chance, int min, int max, Identifier... targetTables) {
        ADDITIONS.put(targetTables, () -> ItemEntry.builder(item)
                .conditionally(RandomChanceLootCondition.builder(chance))
                .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(min, max))));
    }

    /**
     * Injects a single ItemStack entry into the specified LootTable(s)
     *
     * @param stack        The ItemStack to inject
     * @param chance       The chance for the ItemStack to actually generate
     * @param targetTables The LootTable(s) to inject into
     */
    @SuppressWarnings("deprecation")
    public static void injectItemStack(ItemStack stack, float chance, Identifier... targetTables) {
        ADDITIONS.put(targetTables, () -> ItemEntry.builder(stack.getItem())
                .conditionally(RandomChanceLootCondition.builder(chance))
                .apply(() -> SetComponentsLootFunctionAccessor.createSetComponentsLootFunction(List.of(), stack.getComponentChanges()))
                .apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(stack.getCount()))));
    }

    /**
     * Test is {@code target} matches against any of the {@code predicates}.
     * Used to easily target multiple LootTables
     *
     * @param target     The target identifier (this would be the current table)
     * @param predicates The identifiers to test against (this would be the targeted tables)
     * @return {@code true} if target matches any of the predicates
     */
    public static boolean anyMatch(Identifier target, Identifier... predicates) {
        for (var predicate : predicates) if (target.equals(predicate)) return true;
        return false;
    }

    @ApiStatus.Internal
    public static void registerListener() {
        NeoForge.EVENT_BUS.addListener((LootTableLoadEvent event) -> {
            ADDITIONS.forEach((identifiers, lootPoolEntrySupplier) -> {
                if (anyMatch(event.getName(), identifiers)) event.getTable().addPool(LootPool.builder().with(lootPoolEntrySupplier.get()).build());
            });
        });
    }

}
