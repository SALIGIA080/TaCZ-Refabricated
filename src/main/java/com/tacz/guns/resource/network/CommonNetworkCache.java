package com.tacz.guns.resource.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.manager.AttachmentDataManager;
import com.tacz.guns.resource.manager.GunDataManager;
import com.tacz.guns.resource.pojo.AttachmentVariantIndexPOJO;
import com.tacz.guns.resource.pojo.SlotAdapterIndexPOJO;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonAttachmentVariantIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.attachment.SlotAdapterData;
import com.tacz.guns.resource.pojo.data.attachment.VariantData;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

import java.util.*;

/**
 * 网络位置的缓存<br/>
 * 用于存储从网络获取的数据
 */
public enum CommonNetworkCache implements ICommonResourceProvider {
    INSTANCE;

    public Map<Identifier, GunData> gunData = new HashMap<>();
    public Map<Identifier, AttachmentData> attachmentData = new HashMap<>();
    public Map<Identifier, VariantData> attachmentVariantData = new HashMap<>();
    public Map<Identifier, SlotAdapterData> slotAdapterData = new HashMap<>();
    public Map<Identifier, RecipeFilter> recipeFilter = new HashMap<>();
    /** 第 12 轮：服务端同步过来的工作台配方（26.2 客户端无完整配方表）。 */
    public Map<Identifier, TableRecipe> tableRecipe = new HashMap<>();
    public Map<Identifier, BlockData> blockData = new HashMap<>();
    public Map<Identifier, CommonGunIndex> gunIndex = new HashMap<>();
    public Map<Identifier, CommonAmmoIndex> ammoIndex = new HashMap<>();
    public Map<Identifier, CommonAttachmentIndex> attachmentIndex = new HashMap<>();
    public Map<Identifier, CommonAttachmentVariantIndex> attachmentVariantIndex = new HashMap<>();
    public Map<Identifier, CommonSlotAdapterIndex> slotAdapterIndex = new HashMap<>();
    public Map<Identifier, CommonBlockIndex> blockIndex = new HashMap<>();
    public Map<Identifier, Set<String>> attachmentTags = new HashMap<>();
    public Map<Identifier, Set<String>> allowAttachmentTags = new HashMap<>();

    @Nullable
    @Override
    public GunData getGunData(Identifier id) {
        return gunData.get(id);
    }

    @Nullable
    @Override
    public AttachmentData getAttachmentData(Identifier attachmentId) {
        return attachmentData.get(attachmentId);
    }

    @Nullable
    @Override
    public VariantData getAttachmentVariantData(Identifier variantDataId) {
        return attachmentVariantData.get(variantDataId);
    }

    @Nullable
    @Override
    public SlotAdapterData getSlotAdapterData(Identifier adapterDataId) {
        return slotAdapterData.get(adapterDataId);
    }

    @Nullable
    @Override
    public BlockData getBlockData(Identifier id) {
        return blockData.get(id);
    }

    @Nullable
    @Override
    public TableRecipe getTableRecipe(Identifier recipeId) {
        return tableRecipe.get(recipeId);
    }

    @Override
    public java.util.Set<Map.Entry<Identifier, TableRecipe>> getAllTableRecipes() {
        return tableRecipe.entrySet();
    }

    public RecipeFilter getRecipeFilter(Identifier id) {
        return recipeFilter.get(id);
    }

    @Nullable
    @Override
    public CommonGunIndex getGunIndex(Identifier id) {
        return gunIndex.get(id);
    }

    @Override
    public @Nullable CommonAmmoIndex getAmmoIndex(Identifier ammoId) {
        return ammoIndex.get(ammoId);
    }

    @Override
    public @Nullable CommonAttachmentIndex getAttachmentIndex(Identifier attachmentId) {
        return attachmentIndex.get(attachmentId);
    }

    @Override
    public @Nullable CommonAttachmentVariantIndex getAttachmentVariantIndex(Identifier variantId) {
        return attachmentVariantIndex.get(variantId);
    }

    @Override
    public @Nullable CommonSlotAdapterIndex getSlotAdapterIndex(Identifier adapterId) {
        return slotAdapterIndex.get(adapterId);
    }

    @Override
    public @Nullable CommonBlockIndex getBlockIndex(Identifier blockId) {
        return blockIndex.get(blockId);
    }

    @Override
    public @Nullable LuaTable getScript(Identifier scriptId) {
        return null; // 脚本不需要同步
    }

    @Override
    public Set<Map.Entry<Identifier, CommonGunIndex>> getAllGuns() {
        return gunIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAmmoIndex>> getAllAmmos() {
        return ammoIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAttachmentIndex>> getAllAttachments() {
        return attachmentIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAttachmentVariantIndex>> getAllAttachmentVariants() {
        return attachmentVariantIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<Identifier, CommonSlotAdapterIndex>> getAllSlotAdapters() {
        return slotAdapterIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<Identifier, CommonBlockIndex>> getAllBlocks() {
        return blockIndex.entrySet();
    }

    @Override
    public Set<String> getAttachmentTags(Identifier registryName) {
        return attachmentTags.get(registryName);
    }

    @Override
    public Set<String> getAllowAttachmentTags(Identifier registryName) {
        return allowAttachmentTags.get(registryName);
    }

    public void clear() {
        gunData.clear();
        attachmentData.clear();
        attachmentVariantData.clear();
        slotAdapterData.clear();
        gunIndex.clear();
        ammoIndex.clear();
        attachmentIndex.clear();
        attachmentVariantIndex.clear();
        slotAdapterIndex.clear();
        blockIndex.clear();
        recipeFilter.clear();
        tableRecipe.clear();
        blockData.clear();

        attachmentTags.clear();
        allowAttachmentTags.clear();
        AllowAttachmentTagMatcher.resetCache();
    }

    public void fromNetwork(Map<DataType, Map<Identifier, String>> cache) {
        clear();
        // 延后处理
        Map<DataType, Map<Identifier, String>> delayed = new HashMap<>();
        for (Map.Entry<DataType, Map<Identifier, String>> entry : cache.entrySet()) {
            switch (entry.getKey()) {
                case GUN_INDEX:
                case AMMO_INDEX:
                case ATTACHMENT_INDEX:
                case ATTACHMENT_VARIANT_INDEX:
                case SLOT_ADAPTER_INDEX:
                case BLOCK_INDEX:
                    delayed.put(entry.getKey(), entry.getValue());
                    break;
                default:
                    fromNetwork(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<DataType, Map<Identifier, String>> entry : delayed.entrySet()) {
            fromNetwork(entry.getKey(), entry.getValue());
        }
    }

    private <T> T parse(String json, Class<T> dataClass) {
        return CommonAssetsManager.GSON.fromJson(json, dataClass);
    }

    private AttachmentData parseAttachmentData(String json) {
        AttachmentData data = CommonAssetsManager.GSON.fromJson(json, AttachmentData.class);
        JsonElement element = CommonAssetsManager.GSON.fromJson(json, JsonElement.class);
        if (data != null) {
            // 序列化注册的配件属性修改
            AttachmentDataManager.initModifiers(data, element, CommonAssetsManager.GSON);
        }
        return data;
    }

    private GunData parseGunData(String json) {
        GunData data = CommonAssetsManager.GSON.fromJson(json, GunData.class);
        JsonElement element = CommonAssetsManager.GSON.fromJson(json, JsonElement.class);
        if (data != null) {
            GunDataManager.initExclusiveAttachmentModifiers(data, element);
        }
        return data;
    }

    private void resolveAttachmentTags(Map<Identifier, String> data) {
        for (Map.Entry<Identifier, String> entry : data.entrySet()) {
            List<String> parsed = CommonAssetsManager.GSON.fromJson(entry.getValue(), new TypeToken<List<String>>() {
            });
            Set<String> ordered = parsed == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(parsed));
            if (entry.getKey().getPath().startsWith("allow_attachments/") && entry.getKey().getPath().length() > 18) {
                Identifier gunId = entry.getKey().withPath(entry.getKey().getPath().substring(18));
                allowAttachmentTags.put(gunId, ordered);
            } else {
                attachmentTags.put(entry.getKey(), ordered);
            }
        }
    }


    private void fromNetwork(DataType type, Map<Identifier, String> data) {
        for (Map.Entry<Identifier, String> entry : data.entrySet()) {
            try {
                switch (type) {
                    case GUN_DATA -> gunData.put(entry.getKey(), parseGunData(entry.getValue()));
                    case GUN_INDEX -> gunIndex.put(entry.getKey(), parse(entry.getValue(), CommonGunIndex.class));
                    case AMMO_INDEX -> ammoIndex.put(entry.getKey(), parse(entry.getValue(), CommonAmmoIndex.class));
                    case ATTACHMENT_DATA -> attachmentData.put(entry.getKey(), parseAttachmentData(entry.getValue()));
                    case ATTACHMENT_INDEX ->
                            attachmentIndex.put(entry.getKey(), parse(entry.getValue(), CommonAttachmentIndex.class));
                    case ATTACHMENT_VARIANT_INDEX ->
                            attachmentVariantIndex.put(entry.getKey(), CommonAttachmentVariantIndex.getInstance(parse(entry.getValue(), AttachmentVariantIndexPOJO.class)));
                    case SLOT_ADAPTER_INDEX ->
                            slotAdapterIndex.put(entry.getKey(), CommonSlotAdapterIndex.getInstance(parse(entry.getValue(), SlotAdapterIndexPOJO.class)));
                    case ATTACHMENT_VARIANT_DATA -> attachmentVariantData.put(entry.getKey(), parse(entry.getValue(), VariantData.class));
                    case SLOT_ADAPTER_DATA -> slotAdapterData.put(entry.getKey(), parse(entry.getValue(), SlotAdapterData.class));
                    case ATTACHMENT_TAGS -> resolveAttachmentTags(data);
                    case BLOCK_INDEX -> blockIndex.put(entry.getKey(), parse(entry.getValue(), CommonBlockIndex.class));
                    case RECIPE_FILTER -> recipeFilter.put(entry.getKey(), parse(entry.getValue(), RecipeFilter.class));
                    // 第 12 轮：接上此前只声明未接线的 RECIPES 通道
                    case RECIPES -> tableRecipe.put(entry.getKey(), parse(entry.getValue(), TableRecipe.class));
                    case BLOCK_DATA -> blockData.put(entry.getKey(), parse(entry.getValue(), BlockData.class));
                }
            } catch (IllegalArgumentException | JsonParseException exception) {
                GunMod.LOGGER.warn("Failed to parse data from network for {} with id {}", type, entry.getKey(), exception);
            }
        }
    }
}
