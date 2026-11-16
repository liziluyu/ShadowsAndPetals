package com.sshakusora.shadowsandpetals.compat;

import com.mojang.logging.LogUtils;
import com.sshakusora.shadowsandpetals.ShadowsAndPetals;
import com.sshakusora.shadowsandpetals.registries.BlockRegistry;
import com.sshakusora.shadowsandpetals.util.HedgeContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generates normal and tall hedge crafting recipes for registered leaf species. */
@EventBusSubscriber(modid = ShadowsAndPetals.MOD_ID)
public final class HedgeRecipeCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NORMAL_RECIPE_PREFIX = "runtime/hedge/";
    private static final String TALL_RECIPE_PREFIX = "runtime/tall_hedge/";

    // Recipe managers are replaced on datapack reload, but reused when another player joins.
    private static RecipeManager rebuiltManager;
    private static List<RecipeHolder<?>> generatedRecipes = List.of();

    private HedgeRecipeCompat() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuild(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        List<RecipeHolder<?>> generated = rebuild(event.getPlayerList().getServer());
        if (!generated.isEmpty()) {
            event.getRelevantPlayers().forEach(player -> player.awardRecipes(generated));
        }
    }

    public static List<RecipeHolder<?>> rebuild(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager == rebuiltManager) {
            return generatedRecipes;
        }

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            merged.put(holder.id(), holder);
        }

        List<RecipeHolder<?>> generated = new ArrayList<>();
        for (Block leaves : HedgeContents.leafBlocks()) {
            Item leafItem = leaves.asItem();
            if (leafItem == Items.AIR) {
                continue;
            }
            ResourceLocation leavesId = BuiltInRegistries.BLOCK.getKey(leaves);
            addRecipe(merged, generated, recipeId(NORMAL_RECIPE_PREFIX, leavesId),
                    shapedRecipe(leafItem, HedgeContents.create(BlockRegistry.HEDGE.get(), leaves), false));
            addRecipe(merged, generated, recipeId(TALL_RECIPE_PREFIX, leavesId),
                    shapedRecipe(leafItem, HedgeContents.create(BlockRegistry.TALL_HEDGE.get(), leaves), true));
        }

        manager.replaceRecipes(merged.values());
        generatedRecipes = List.copyOf(generated);
        rebuiltManager = manager;
        LOGGER.info("Generated {} runtime hedge recipes", generatedRecipes.size());
        return generatedRecipes;
    }

    private static void addRecipe(
            Map<ResourceLocation, RecipeHolder<?>> merged,
            List<RecipeHolder<?>> generated,
            ResourceLocation id,
            ShapedRecipe recipe
    ) {
        RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(id, recipe);
        if (merged.putIfAbsent(id, holder) == null) {
            generated.add(holder);
        } else {
            LOGGER.warn("Not generating hedge recipe {}: id already used by another recipe", id);
        }
    }

    private static ShapedRecipe shapedRecipe(Item leafItem, ItemStack result, boolean tall) {
        ShapedRecipePattern pattern = ShapedRecipePattern.of(
                Map.of('L', Ingredient.of(leafItem), 'S', Ingredient.of(Items.STICK)),
                tall ? List.of("L", "L", "S") : List.of("L", "S"));
        return new ShapedRecipe("", CraftingBookCategory.BUILDING, pattern, result);
    }

    private static ResourceLocation recipeId(String prefix, ResourceLocation leavesId) {
        return ShadowsAndPetals.asResource(prefix + leavesId.getNamespace() + "/" + leavesId.getPath());
    }
}
