package com.tacz.guns.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.util.ContentPackOrder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Read-only gun-pack migration audit command (/tacz verify).
 *
 * <p>Prints key migration indicators to <b>in-game chat + log</b> (log uses the
 * {@code [TACZ Verify]} prefix, readable by the main instance's DebugBridge Web UI):
 * <ol>
 *   <li>Gun / ammo / attachment / block / recipe totals, grouped by namespace;</li>
 *   <li>Whether namespace order puts {@code tacz} first (via {@link ContentPackOrder});</li>
 *   <li>How many recipe results resolve a non-empty group (and which ones don't).</li>
 * </ol>
 *
 * <p>Labels are ASCII-only on purpose: Chinese labels get mangled when the message is
 * routed through the chat/HUD font path on zh_CN clients (and by DebugBridge's log
 * capture), so diagnostic output stays ASCII to avoid corruption.
 */
public class VerifyCommand {
    private static final String NAME = "verify";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal(NAME).executes(VerifyCommand::verify);
    }

    private static int verify(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CommonAssetsManager manager = CommonAssetsManager.getInstance();

        Set<Map.Entry<Identifier, CommonGunIndex>> guns = manager == null ? java.util.Collections.emptySet() : manager.getAllGuns();
        Set<Map.Entry<Identifier, CommonAmmoIndex>> ammos = manager == null ? java.util.Collections.emptySet() : manager.getAllAmmos();
        Set<Map.Entry<Identifier, CommonAttachmentIndex>> attachments = manager == null ? java.util.Collections.emptySet() : manager.getAllAttachments();
        Set<Map.Entry<Identifier, CommonBlockIndex>> blocks = manager == null ? java.util.Collections.emptySet() : manager.getAllBlocks();
        Set<Map.Entry<Identifier, TableRecipe>> recipes = manager == null ? java.util.Collections.emptySet() : manager.getAllTableRecipes();

        report(source, "[TACZ Verify] gun=%d ammo=%d attachment=%d block=%d recipe=%d",
                guns.size(), ammos.size(), attachments.size(), blocks.size(), recipes.size());

        report(source, "[TACZ Verify] namespace breakdown:");
        reportNamespace(source, "gun", guns.stream().collect(Collectors.groupingBy(e -> e.getKey().getNamespace(), Collectors.counting())));
        reportNamespace(source, "ammo", ammos.stream().collect(Collectors.groupingBy(e -> e.getKey().getNamespace(), Collectors.counting())));
        reportNamespace(source, "attachment", attachments.stream().collect(Collectors.groupingBy(e -> e.getKey().getNamespace(), Collectors.counting())));
        reportNamespace(source, "block", blocks.stream().collect(Collectors.groupingBy(e -> e.getKey().getNamespace(), Collectors.counting())));
        reportNamespace(source, "recipe", recipes.stream().collect(Collectors.groupingBy(e -> e.getKey().getNamespace(), Collectors.counting())));

        // Sort check: does ContentPackOrder put tacz first?
        List<String> namespaces = guns.stream().map(e -> e.getKey().getNamespace()).distinct()
                .sorted(ContentPackOrder.NAMESPACE_ORDER).collect(Collectors.toList());
        report(source, "[TACZ Verify] order(ContentPackOrder)=%s", namespaces);
        if (!namespaces.isEmpty() && namespaces.get(0).equals(GunMod.MOD_ID)) {
            report(source, "[TACZ Verify] order check OK: tacz first");
        } else {
            report(source, "[TACZ Verify] order check FAIL: tacz not first, namespaces=%s", namespaces);
        }

        // Recipe result group resolution check: list the empty ones by id for diagnosis.
        int resolved = 0;
        List<String> emptyIds = new ArrayList<>();
        for (Map.Entry<Identifier, TableRecipe> entry : recipes) {
            TableRecipe pojo = entry.getValue();
            if (pojo == null || pojo.getResult() == null) {
                emptyIds.add(entry.getKey() + "(null result)");
                continue;
            }
            Identifier group = pojo.getResult().getGroup();
            if (group != null && !group.toString().endsWith(":empty")) {
                resolved++;
            } else {
                emptyIds.add(entry.getKey().toString());
            }
        }
        report(source, "[TACZ Verify] recipe group resolved=%d empty=%d (total=%d)", resolved, emptyIds.size(), recipes.size());
        if (!emptyIds.isEmpty()) {
            report(source, "[TACZ Verify]   empty-group recipes: %s", emptyIds);
        }

        report(source, "[TACZ Verify] done. Non-zero gun/attachment/recipe counts + OK order => packs loaded correctly.");
        return Command.SINGLE_SUCCESS;
    }

    private static void reportNamespace(CommandSourceStack source, String label, Map<String, Long> counts) {
        if (counts.isEmpty()) {
            report(source, "[TACZ Verify]   %s: none", label);
            return;
        }
        Map<String, Long> sorted = new TreeMap<>(counts);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : sorted.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(' ');
        }
        report(source, "[TACZ Verify]   %s: %s", label, sb.toString().trim());
    }

    private static void report(CommandSourceStack source, String format, Object... args) {
        String msg = String.format(format, args);
        GunMod.LOGGER.info(msg);
        if (source.getEntity() != null) {
            source.sendSystemMessage(Component.literal(msg));
        }
    }
}
