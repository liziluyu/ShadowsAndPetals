package com.sshakusora.shadowsandpetals.data.model;

import com.sshakusora.shadowsandpetals.ShadowsAndPetals;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SAPBlockModelGenerator {
    private final @Nullable BlockStateProvider provider;
    private final Set<String> createdModelPaths = new HashSet<>();

    public SAPBlockModelGenerator() {
        this(null);
    }

    public SAPBlockModelGenerator(@Nullable BlockStateProvider provider) {
        this.provider = provider;
    }

    public ResourceLocation modLoc(String path) {
        return ShadowsAndPetals.asResource(path);
    }

    public ResourceLocation blockModelId(Block block) {
        return ShadowsAndPetals.asResource("block/" + BuiltInRegistries.BLOCK.getKey(block).getPath());
    }

    public void suggestItemModel(Item item, ResourceLocation model) {
        if (provider == null) {
            return;
        }
        String name = item.builtInRegistryHolder().key().location().getPath();
        provider.itemModels().getBuilder(name).parent(new ModelFile.UncheckedModelFile(model));
    }

    public BlockStateProvider provider() {
        if (provider == null) {
            throw new IllegalStateException("This generator is not attached to a BlockStateProvider");
        }
        return provider;
    }

    /** Create a generated block model whose parent and texture slots are resources. */
    public ModelFile createModel(String path, ResourceLocation parent, Map<String, ResourceLocation> textures) {
        BlockModelBuilder builder = provider().models().getBuilder(path)
                .parent(provider().models().getExistingFile(parent));
        textures.forEach((slot, texture) -> {
            ensureVanillaTexture(texture);
            builder.texture(slot, texture);
        });
        return builder;
    }

    public ModelFile createModel(String path, @Nullable ResourceLocation parent,
                                 Map<String, ResourceLocation> textures, @Nullable String renderType) {
        BlockModelBuilder builder = provider().models().getBuilder(path);
        if (parent != null) {
            builder.parent(provider().models().getExistingFile(parent));
        }
        textures.forEach((slot, texture) -> {
            ensureVanillaTexture(texture);
            builder.texture(slot, texture);
        });
        if (renderType != null) {
            builder.renderType(renderType);
        }
        return builder;
    }

    /**
     * Reserves a model path for one-time generation.
     *
     * <p>This mirrors the old generator's {@code jsonModelOnce} behavior. It
     * is needed for shared connection models which are requested once for each
     * wood post but must contain one fixed set of elements.</p>
     */
    public boolean shouldCreateModelOnce(String path) {
        return createdModelPaths.add(path);
    }

    public ModelFile createObjModel(String path, ResourceLocation parent, ResourceLocation obj,
                                    Map<String, ResourceLocation> textures, boolean flipV) {
        BlockModelBuilder builder = (BlockModelBuilder) createModel(path, parent, textures, null);
        builder.customLoader((model, helper) -> ObjModelBuilder.begin(model, helper)
                .modelLocation(obj)
                .flipV(flipV));
        return builder;
    }

    private void ensureVanillaTexture(ResourceLocation texture) {
        if (provider != null && "minecraft".equals(texture.getNamespace())
                && !provider.models().existingFileHelper.exists(texture,
                new ExistingFileHelper.ResourceType(PackType.CLIENT_RESOURCES, ".png", "textures"))) {
            provider.models().existingFileHelper.trackGenerated(texture,
                    new ExistingFileHelper.ResourceType(PackType.CLIENT_RESOURCES, ".png", "textures"));
        }
    }

    /** Create a model inheriting a hand-authored model with a root translation. */
    public ResourceLocation createTranslatedParentModel(String path, ResourceLocation parent,
                                                        float x, float y, float z) {
        BlockModelBuilder builder = provider().models().getBuilder(path)
                .parent(provider().models().getExistingFile(parent));
        builder.rootTransforms().translation(x, y, z).end();
        return builder.getLocation();
    }

    /** Reference a model that is generated by another provider in this run. */
    public ModelFile uncheckedModel(ResourceLocation model) {
        return new ModelFile.UncheckedModelFile(model);
    }

    /**
     * Emits the legacy block model, blockstate and block-item model for a
     * simple cube callback.
     */
    public void cubeAllWithItem(Block block, String name, ResourceLocation texture) {
        if (provider == null) {
            return;
        }
        ModelFile model = provider.models().cubeAll(name, texture);
        provider.simpleBlockWithItem(block, model);
    }

    public void simpleBlockWithItem(Block block, ResourceLocation model) {
        if (provider == null) {
            return;
        }
        provider.simpleBlockWithItem(block, provider.models().getExistingFile(model));
    }

    public void simpleBlockItem(Block block, ResourceLocation model) {
        if (provider == null) {
            return;
        }
        provider.simpleBlockItem(block, new ModelFile.UncheckedModelFile(model));
    }

}
