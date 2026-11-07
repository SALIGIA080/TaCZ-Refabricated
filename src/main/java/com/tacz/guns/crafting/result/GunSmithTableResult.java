package com.tacz.guns.crafting.result;

import cn.sh1rocu.tacz.util.forge.CraftingHelper;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.recipe.GunResult;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GunSmithTableResult {
    /**
     * 供 {@link com.tacz.guns.resource.pojo.data.recipe.TableRecipe#CODEC}（通用转换器校验用）解码。
     *
     * <p>本 codec 只服务于 {@code UniversalPackConverter} 的<b>输出校验</b>（转换器已把 result
     * 规范化成两种形态之一，且 materials 的 item 已单独经 {@code Ingredient.CODEC} 校验过）。
     * 因此这里做「结构解码 + 类型归一化」即可，<b>不真正构造物品</b>（对齐 {@code GunSmithTableResult}
     * 的两阶段初始化语义：custom 物品走 {@code CraftingHelper} 懒解析，typed 结果走 {@link RawGunTableResult}）。
     *
     * <p>与官方 1.2.0 的 {@code GunSmithTableResult#decode} 对齐，但做了 26.2 适配：
     * <ul>
     *   <li>{@code ResourceLocation} → {@code Identifier}；</li>
     *   <li>custom 结果不在此处 {@code ItemStack.OPTIONAL_CODEC} 当场构造（26.2 reload 阶段组件未 bind 会抛），
     *       而是存 {@code rawCustomItem} 延后到 {@link #init()}；</li>
     *   <li>encode 一律抛 {@code UnsupportedOperationException}（官方同款，仅解码方向使用）。</li>
     * </ul>
     */
    public static final Codec<GunSmithTableResult> CODEC = Codec.of(GunSmithTableResult::encode, GunSmithTableResult::decode);
    public static final StreamCodec<RegistryFriendlyByteBuf, GunSmithTableResult> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, GunSmithTableResult::getResult,
            Identifier.STREAM_CODEC, GunSmithTableResult::getGroup,
            GunSmithTableResult::new
    );

    public static <T> DataResult<T> encode(GunSmithTableResult input, DynamicOps<T> ops, T prefix) {
        throw new UnsupportedOperationException("GunSmithTableResult is decode-only for conversion validation");
    }

    public static <T> DataResult<Pair<GunSmithTableResult, T>> decode(DynamicOps<T> ops, T input) {
        return ops.getMap(input).map(map -> {
            String typeName = Codec.STRING.fieldOf("type").decode(ops, map).getOrThrow();
            int count = Codec.INT.optionalFieldOf("count", 1).decode(ops, map).getOrThrow();
            CompoundTag extraTag = CompoundTag.CODEC.optionalFieldOf("nbt", null).decode(ops, map).getOrThrow();
            if (map.get("nbt") != null && map.get("components") != null) {
                throw new IllegalArgumentException("Recipe result cannot specify both nbt and components");
            }
            DataComponentPatch components = DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .decode(ops, map).getOrThrow();
            Identifier tabOverride = Codec.STRING.optionalFieldOf("group").decode(ops, map).getOrThrow()
                    .map(raw -> raw.contains(":") ? raw : GunMod.MOD_ID + ":" + raw)
                    .map(Identifier::tryParse).orElse(null);

            GunSmithTableResult result;
            switch (typeName) {
                case GUN, AMMO, ATTACHMENT -> {
                    Identifier id = Identifier.CODEC.fieldOf("id").decode(ops, map).getOrThrow();
                    RawGunTableResult raw = new RawGunTableResult(typeName, id, count);
                    if (extraTag != null) raw.setNbt(extraTag);
                    if (typeName.equals(GUN)) {
                        GunResult.CODEC.parse(ops, input).result().ifPresent(raw::setExtraData);
                    }
                    result = new GunSmithTableResult(raw, tabOverride);
                }
                case CUSTOM -> {
                    // 26.2 适配：不在此处用 ItemStack.OPTIONAL_CODEC 当场构造（reload 阶段组件未 bind 会抛）。
                    // 转成 JsonObject 存 rawCustomItem，延后到 init() 用 CraftingHelper 懒解析。
                    // ops 为 RegistryOps<JsonElement>（见 LegacyItemStackUpgrader#ops），convertTo(JsonOps.INSTANCE, ...) 是恒等转换。
                    com.google.gson.JsonElement itemElement = ops.convertTo(com.mojang.serialization.JsonOps.INSTANCE, map.get("item"));
                    if (!itemElement.isJsonObject()) {
                        throw new IllegalArgumentException("Custom recipe result item must be a JSON object");
                    }
                    result = new GunSmithTableResult(itemElement.getAsJsonObject(), tabOverride);
                }
                default -> result = new GunSmithTableResult(ItemStack.EMPTY, TabConfig.TAB_EMPTY);
            }
            return Pair.of(result, input);
        });
    }

    private static final Identifier EMPTY_GROUP = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "empty");
    public static final String GUN = "gun";
    public static final String AMMO = "ammo";
    public static final String ATTACHMENT = "attachment";
    public static final String CUSTOM = "custom";

    private ItemStack result = ItemStack.EMPTY;
    private Identifier group = null;

    @Nullable
    private RawGunTableResult raw = null;
    @Nullable
    private JsonObject rawCustomItem = null;

    public GunSmithTableResult(ItemStack result, @Nullable Identifier group) {
        this.result = result;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    public GunSmithTableResult(@NotNull JsonObject rawCustomItem, @Nullable Identifier group) {
        // CUSTOM result must be parsed lazily. During resource reload, 26.2 item components may not be
        // bound yet; constructing ItemStack immediately can throw "Components not bound yet".
        this.rawCustomItem = rawCustomItem;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw) {
        this.raw = raw;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw, @Nullable Identifier group) {
        this.raw = raw;
        this.group = group == null ? EMPTY_GROUP : group;
    }

    public void init() {
        if (raw != null) {
            GunSmithTableResult result = RawGunTableResult.init(raw);
            this.result = result.getResult();
            if (group == null || group.equals(EMPTY_GROUP)) {
                this.group = result.getGroup();
            }
            this.raw = null;
        }
        if (rawCustomItem != null) {
            // 容错边界：本方法会在「收集全部配方 → 逐条 init()」的循环里被调用
            // （JEI GunModPlugin、REI REIClientPlugin、GunSmithTableScreen、GunSmithTableMenu）。
            // 若此处因 JSON 格式错误抛出（例如内层键误写成 "id" 而非 "item"），
            // 一条坏配方就会中断整个循环，导致全部工作台配方从 JEI/REI 消失。
            // raw 路径（RawGunTableResult.init）对坏数据一向降级为 EMPTY + 空页签而非抛出，
            // 这里对齐同样语义：归入 EMPTY_GROUP 后，各处的页签过滤会自然剔除本条配方。
            try {
                this.result = CraftingHelper.getItemStack(rawCustomItem, true);
            } catch (RuntimeException e) {
                GunMod.LOGGER.error("Failed to parse custom gun smith table result item: {}", rawCustomItem, e);
                this.result = ItemStack.EMPTY;
                this.group = EMPTY_GROUP;
            }
            this.rawCustomItem = null;
        }
    }

    public ItemStack getResult() {
        return result;
    }

    public Identifier getGroup() {
        return group;
    }
}
