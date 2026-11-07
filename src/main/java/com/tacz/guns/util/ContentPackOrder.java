package com.tacz.guns.util;

import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.Map;

/**
 * 内容包命名空间排序：保证 {@code tacz} 命名空间（官方枪包）的条目优先于第三方枪包。
 *
 * <p>官方 1.2.0 的 {@code ContentPackOrder}（1.21.1 NeoForge），此处做 26.2 适配：
 * {@code ResourceLocation} → {@code Identifier}。语义不变：
 * <ol>
 *   <li>先按命名空间比较——{@code tacz} 排最前（权重 0），其余命名空间按自然序（权重 1 + 字典序）；</li>
 *   <li>再叠加各索引的 {@code sort} 字段，得到稳定的「官方优先 + 内部排序号」次序。</li>
 * </ol>
 */
public final class ContentPackOrder {
    public static final Comparator<String> NAMESPACE_ORDER = Comparator
            .comparingInt((String namespace) -> GunMod.MOD_ID.equals(namespace) ? 0 : 1)
            .thenComparing(Comparator.naturalOrder());

    private ContentPackOrder() {
    }

    public static <T> Comparator<Map.Entry<Identifier, T>> byNamespace() {
        return Comparator.comparing(entry -> entry.getKey().getNamespace(), NAMESPACE_ORDER);
    }
}
