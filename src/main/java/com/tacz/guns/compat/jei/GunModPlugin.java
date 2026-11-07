package com.tacz.guns.compat.jei;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.compat.jei.category.AmmoQueryCategory;
import com.tacz.guns.compat.jei.category.AttachmentQueryCategory;
import com.tacz.guns.compat.jei.category.GunAmmoQueryCategory;
import com.tacz.guns.compat.jei.category.GunAttachmentQueryCategory;
import com.tacz.guns.compat.jei.category.GunSmithTableCategory;
import com.tacz.guns.compat.jei.entry.AmmoQueryEntry;
import com.tacz.guns.compat.jei.entry.AttachmentQueryEntry;
import com.tacz.guns.compat.jei.entry.GunAmmoQueryEntry;
import com.tacz.guns.compat.jei.entry.GunAttachmentQueryEntry;
import com.tacz.guns.compat.jei.transfer.GunSmithTableTransferHandler;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JeiPlugin
public class GunModPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "jei");

    private Map<Identifier, IRecipeType<GunSmithTableRecipe>> recipeTypeMap = new HashMap<>();

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        recipeTypeMap.clear();
        var map = TimelessAPI.getAllCommonBlockIndex();
        for (var entry : map) {
            BlockItem item = entry.getValue().getBlock();
            ItemStack icon = BlockItemBuilder.create(item).setId(entry.getKey()).build();
            IRecipeType<GunSmithTableRecipe> type = IRecipeType.create(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table/" + entry.getKey().toString().replace(':', '_')), GunSmithTableRecipe.class);
            registration.addRecipeCategories(new GunSmithTableCategory(registration.getJeiHelpers().getGuiHelper(), icon, type, item.getName(icon)));
            recipeTypeMap.put(entry.getKey(), type);
        }
        registration.addRecipeCategories(
                new AttachmentQueryCategory(registration.getJeiHelpers().getGuiHelper()),
                new GunAttachmentQueryCategory(registration.getJeiHelpers().getGuiHelper()),
                new GunAmmoQueryCategory(registration.getJeiHelpers().getGuiHelper()),
                new AmmoQueryCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return;
        // 第 12 轮：与 GunSmithTableScreen 一致，改用 CommonAssetsManager.get()。
        // getInstance() 是纯服务端实例，在多人客户端恒为 null —— JEI 在单人能用
        // 只是因为同 JVM 共享了服务端实例，连专用服务器时同样会空。
        List<GunSmithTableRecipe> recipes = new java.util.ArrayList<>();
        for (var e : com.tacz.guns.resource.CommonAssetsManager.get().getAllTableRecipes()) {
            if (e.getValue() != null && e.getValue().getResult() != null) {
                GunSmithTableRecipe r = new GunSmithTableRecipe(e.getKey(), e.getValue());
                try {
                    r.init();   // 解析 raw result，否则 getResult() 恒为 EMPTY
                } catch (RuntimeException ex) {
                    // 一条坏配方绝不能中断整个循环 —— 否则全部工作台配方都会从 JEI 消失。
                    GunMod.LOGGER.error("Failed to init gun smith table recipe {} for JEI, skipping it", e.getKey(), ex);
                    continue;
                }
                recipes.add(r);
            }
        }

        for (var entry : recipeTypeMap.entrySet()) {
            TimelessAPI.getCommonBlockIndex(entry.getKey()).ifPresent(blockIndex -> {
                List<GunSmithTableRecipe> recipeList = blockIndex.getFilter().filter(recipes, GunSmithTableRecipe::getId);
                recipeList.removeIf(recipe -> {
                    return blockIndex.getData().getTabs().stream().noneMatch(tab -> Objects.equals(tab.id(), recipe.getResult().getGroup()));
                });
                registration.addRecipes(entry.getValue(), recipeList);
            });
        }

        registration.addRecipes(AttachmentQueryCategory.ATTACHMENT_QUERY,
                AttachmentQueryEntry.getAllAttachmentQueryEntries());
        registration.addRecipes(GunAttachmentQueryCategory.GUN_ATTACHMENT_QUERY,
                GunAttachmentQueryEntry.getAllGunAttachmentQueryEntries());
        List<GunAmmoQueryEntry> gunAmmoEntries = GunAmmoQueryEntry.getAllGunAmmoQueryEntries();
        registration.addRecipes(GunAmmoQueryCategory.GUN_AMMO_QUERY, gunAmmoEntries);
        registration.addRecipes(AmmoQueryCategory.AMMO_QUERY,
                AmmoQueryEntry.getAllAmmoQueryEntries(gunAmmoEntries));

        registerAmmoStorageUpgradeRecipes(registration);
        registerGunPegboardDyeRecipe(registration);
    }

    /**
     * 金/钻石弹药库升级配方是 CustomRecipe，JEI 默认不显示。
     * 这里用 vanilla 形状配方构造显示条目，让它们在「合成」页可见。
     * 实际合成仍走 {@link com.tacz.guns.crafting.AmmoStorageUpgradeRecipe}（保留弹药数据）。
     */
    private void registerAmmoStorageUpgradeRecipes(IRecipeRegistration registration) {
        var factory = registration.getVanillaRecipeFactory();
        List<RecipeHolder<CraftingRecipe>> holders = new java.util.ArrayList<>();
        holders.add(buildUpgradeHolder(factory, "ammo_storage_gold", ModItems.AMMO_STORAGE, net.minecraft.world.item.Items.GOLD_INGOT, ModItems.AMMO_STORAGE_GOLD));
        holders.add(buildUpgradeHolder(factory, "ammo_storage_diamond", ModItems.AMMO_STORAGE_GOLD, net.minecraft.world.item.Items.DIAMOND, ModItems.AMMO_STORAGE_DIAMOND));
        registration.addRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, holders);
    }

    private RecipeHolder<CraftingRecipe> buildUpgradeHolder(
            mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory factory,
            String name, net.minecraft.world.item.Item from, net.minecraft.world.item.Item material, net.minecraft.world.item.Item to) {
        var builder = factory.createShapedRecipeBuilder(
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(to));
        builder.define('A', net.minecraft.world.item.crafting.Ingredient.of(from));
        builder.define('M', net.minecraft.world.item.crafting.Ingredient.of(material));
        builder.pattern("MMM");
        builder.pattern("MAM");
        builder.pattern("MMM");
        CraftingRecipe recipe = builder.build();
        Identifier id = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name);
        ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id);
        return new RecipeHolder<>(key, recipe);
    }

    /**
     * 挂板染色配方（gun_pegboard_dye）也是 CustomRecipe，JEI 默认不显示。
     * 照抄弹药库升级的做法：用 vanilla 形状配方构造「挂板 + 染料」展示条目，让它在「合成」页可见。
     * 实际合成仍走 {@link com.tacz.guns.crafting.GunPegboardDyeRecipe}（对挂板染上染料颜色）。
     */
    private void registerGunPegboardDyeRecipe(IRecipeRegistration registration) {
        try {
            var factory = registration.getVanillaRecipeFactory();
            List<RecipeHolder<CraftingRecipe>> holders = new java.util.ArrayList<>();
            // 产物用普通挂板示意（实际合成结果是带 DYED_COLOR 组件的染色挂板，JEI 的 ItemSlotDisplay 无法携带组件，只能显示物品类型）。
            var builder = factory.createShapedRecipeBuilder(
                    net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                    new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(ModItems.GUN_PEGBOARD));
            builder.define('P', net.minecraft.world.item.crafting.Ingredient.of(ModItems.GUN_PEGBOARD));
            // 染料用红色染料示意（任意染料均可，实际配方接受 16 色中的任意一种）。
            builder.define('D', net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.DYE.pick(net.minecraft.world.item.DyeColor.RED)));
            builder.pattern("PD");
            CraftingRecipe recipe = builder.build();
            // 用独立 id，避免与真实 CustomRecipe（tacz:gun_pegboard_dye）冲突导致 JEI 去重丢弃。
            Identifier id = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_pegboard_dye_display");
            ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id);
            holders.add(new RecipeHolder<>(key, recipe));
            registration.addRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, holders);
            GunMod.LOGGER.info("[JEI] registered gun_pegboard_dye display recipe");
        } catch (RuntimeException ex) {
            GunMod.LOGGER.error("[JEI] failed to register gun_pegboard_dye display recipe", ex);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var entry : recipeTypeMap.entrySet()) {
            TimelessAPI.getCommonBlockIndex(entry.getKey()).ifPresent(blockIndex -> {
                ItemStack stack = BlockItemBuilder.create(blockIndex.getBlock()).setId(entry.getKey()).build();
                registration.addCraftingStation(entry.getValue(), stack);
            });

        }

    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // 1025：为每个枪械工作台配方注册物品转移处理器，让 JEI 工作台配方页支持
        // 「+」号把配方一键转移到枪械工作台界面（requestRecipeSelection）。
        for (var type : recipeTypeMap.values()) {
            registration.addRecipeTransferHandler(new GunSmithTableTransferHandler(type, registration.getTransferHelper()), type);
        }
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.AMMO, GunModSubtype.getAmmoSubtype());
        registration.registerSubtypeInterpreter(ModItems.ATTACHMENT, GunModSubtype.getAttachmentSubtype());
        registration.registerSubtypeInterpreter(ModItems.AMMO_BOX, GunModSubtype.getAmmoBoxSubtype());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_111, GunModSubtype.getTableSubType());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_121, GunModSubtype.getTableSubType());
        registration.registerSubtypeInterpreter(ModItems.WORKBENCH_211, GunModSubtype.getTableSubType());
        GunItemManager.getAllGunItems().forEach(item -> registration.registerSubtypeInterpreter(item, GunModSubtype.getGunSubtype()));
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }
}
