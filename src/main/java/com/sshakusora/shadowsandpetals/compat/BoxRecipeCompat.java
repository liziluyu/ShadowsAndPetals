package com.sshakusora.shadowsandpetals.compat;

import com.mojang.logging.LogUtils;
import com.sshakusora.shadowsandpetals.ShadowsAndPetals;
import com.sshakusora.shadowsandpetals.registries.BlockEntityRegistry;
import com.sshakusora.shadowsandpetals.registries.BlockRegistry;
import com.sshakusora.shadowsandpetals.util.BoxContents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generates box recipes from the item tags present in the running instance. */
@EventBusSubscriber(modid = ShadowsAndPetals.MOD_ID)
public final class BoxRecipeCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COMPAT_PREFIX = "compat/";

    // Both events run on the server thread. A new RecipeManager is installed by every datapack
    // reload, so identity caching avoids rescanning every recipe whenever another player joins.
    private static RecipeManager rebuiltManager;
    private static List<RecipeHolder<?>> generatedRecipes = List.of();

    private BoxRecipeCompat() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuild(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        List<RecipeHolder<?>> generated = rebuild(event.getPlayerList().getServer());
        if (!generated.isEmpty()) {
            // Runtime recipes have no static unlock advancement. Use the same public recipe-book
            // path as vanilla advancement rewards before the initial recipe-book sync below.
            event.getRelevantPlayers().forEach(player -> player.awardRecipes(generated));
        }
    }

    /** Adds one shapeless recipe per tagged content item not already covered by a box recipe. */
    public static List<RecipeHolder<?>> rebuild(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager == rebuiltManager) {
            return generatedRecipes;
        }

        HolderLookup.Provider registries = server.registryAccess();
        Item box = BlockRegistry.BOX.get().asItem();
        BlockEntityType<?> boxType = BlockEntityRegistry.BOX.get();
        Set<ResourceLocation> covered = coveredContent(manager, box, registries);

        Map<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            merged.put(holder.id(), holder);
        }

        List<RecipeHolder<?>> generated = new ArrayList<>();
        for (Item content : BoxContents.taggedContentItems(item -> true)) {
            if (content == box) {
                continue;
            }

            ResourceLocation contentId = BuiltInRegistries.ITEM.getKey(content);
            if (contentId == null || covered.contains(contentId)) {
                continue;
            }

            ResourceLocation recipeId = compatRecipeId(contentId);
            if (merged.containsKey(recipeId)) {
                LOGGER.warn("Not generating box recipe {}: id already used by another recipe", recipeId);
                continue;
            }

            ItemStack result = BoxContents.createContentBox(box, content, boxType);
            ShapelessRecipe recipe = new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    result,
                    ingredients(box, content)
            );
            RecipeHolder<ShapelessRecipe> holder = new RecipeHolder<>(recipeId, recipe);
            merged.put(recipeId, holder);
            generated.add(holder);
        }

        manager.replaceRecipes(merged.values());
        generatedRecipes = List.copyOf(generated);
        rebuiltManager = manager;
        LOGGER.info("Generated {} runtime box content recipes", generatedRecipes.size());
        return generatedRecipes;
    }

    /** Finds content already produced by static or datapack recipes, regardless of recipe id. */
    private static Set<ResourceLocation> coveredContent(
            RecipeManager manager,
            Item box,
            HolderLookup.Provider registries
    ) {
        Set<ResourceLocation> covered = new HashSet<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            ItemStack result;
            try {
                result = holder.value().getResultItem(registries);
            } catch (RuntimeException exception) {
                // A third-party recipe with an unresolved result must not block server reload.
                continue;
            }
            if (!result.is(box)) {
                continue;
            }

            String rawContent = BoxContents.contentId(result);
            ResourceLocation contentId = rawContent == null ? null : ResourceLocation.tryParse(rawContent);
            if (contentId != null) {
                covered.add(contentId);
            }
        }
        return covered;
    }

    private static NonNullList<Ingredient> ingredients(Item box, Item content) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(9, Ingredient.EMPTY);
        ingredients.set(0, Ingredient.of(box));
        Ingredient contentIngredient = Ingredient.of(content);
        for (int slot = 1; slot < 9; slot++) {
            ingredients.set(slot, contentIngredient);
        }
        return ingredients;
    }

    private static ResourceLocation compatRecipeId(ResourceLocation contentId) {
        return ShadowsAndPetals.asResource(
                COMPAT_PREFIX + contentId.getNamespace() + "/" + contentId.getPath() + "_box"
        );
    }
}
