package com.minigameplayground.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.minigameplayground.MiniGamePlaygroundConfig;
import com.minigameplayground.damage.DamageProcessor;
import com.minigameplayground.damage.HealingProcessor;
import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import com.minigameplayground.zone.ZoneSnapshots;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlaygroundCommand {
    private static final int OP_LEVEL = 2;
    private static final double MIN_HEALTH = 0.1D;
    private static final double MAX_HEALTH = 100_000.0D;

    private static final SuggestionProvider<CommandSourceStack> ZONE_SUGGESTIONS = (context, builder) -> {
        for (String id : ZoneManager.getZoneIds()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> DIMENSION_SUGGESTIONS = (context, builder) -> {
        for (ResourceKey<Level> key : context.getSource().getServer().levelKeys()) {
            builder.suggest(key.location().toString());
        }
        return builder.buildFuture();
    };

    private PlaygroundCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot("mgp"));
        dispatcher.register(buildRoot("minigameplayground"));
        dispatcher.register(buildRoot("pvb"));
        dispatcher.register(buildRoot("pvpbalance"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .requires(source -> source.hasPermission(OP_LEVEL))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), null))
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .suggests(DIMENSION_SUGGESTIONS)
                                .executes(context -> list(context.getSource(),
                                        ResourceLocationArgument.getId(context, "dimension")))))
                .then(Commands.literal("check")
                        .executes(PlaygroundCommand::check))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())));

        root.then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.string())
                        .then(Commands.literal("dimension")
                                .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                        .suggests(DIMENSION_SUGGESTIONS)
                                        .then(Commands.argument("pos1", Vec3Argument.vec3(false))
                                                .then(Commands.argument("pos2", Vec3Argument.vec3(false))
                                                        .executes(PlaygroundCommand::createInDimension)))))
                        .then(Commands.argument("pos1", Vec3Argument.vec3(false))
                                .then(Commands.argument("pos2", Vec3Argument.vec3(false))
                                        .executes(PlaygroundCommand::create)))));

        root.then(Commands.literal("remove")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .executes(PlaygroundCommand::remove)));

        root.then(damageBaselineCommand());
        root.then(healBaselineCommand());

        root.then(Commands.literal("unbreakable")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(PlaygroundCommand::setUnbreakable))));

        root.then(Commands.literal("reset")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .executes(PlaygroundCommand::reset)));

        root.then(Commands.literal("snapshot")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .executes(PlaygroundCommand::snapshot)));

        root.then(Commands.literal("damage_rate")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> damageRate(context, null))
                        .then(Commands.argument("zone", StringArgumentType.string())
                                .suggests(ZONE_SUGGESTIONS)
                                .executes(context -> damageRate(context,
                                        StringArgumentType.getString(context, "zone"))))));

        root.then(Commands.literal("heal_rate")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> healRate(context, null))
                        .then(Commands.argument("zone", StringArgumentType.string())
                                .suggests(ZONE_SUGGESTIONS)
                                .executes(context -> healRate(context,
                                        StringArgumentType.getString(context, "zone"))))));

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> damageBaselineCommand() {
        return Commands.literal("damage")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .then(Commands.literal("disable")
                                .executes(PlaygroundCommand::disableDamage))
                        .then(Commands.argument("value",
                                        DoubleArgumentType.doubleArg(MIN_HEALTH, MAX_HEALTH))
                                .executes(PlaygroundCommand::setDamageBaseline)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> healBaselineCommand() {
        return Commands.literal("heal")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .then(Commands.literal("disable")
                                .executes(PlaygroundCommand::disableHeal))
                        .then(Commands.argument("value",
                                        DoubleArgumentType.doubleArg(MIN_HEALTH, MAX_HEALTH))
                                .executes(PlaygroundCommand::setHealBaseline)));
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        return create(context, context.getSource().getLevel());
    }

    private static int createInDimension(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");

        ServerLevel level = source.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            source.sendFailure(Component.literal(
                    "[MGP] 找不到维度 '" + dimensionId + "'，或该维度未加载。"));
            return 0;
        }
        return create(context, level);
    }

    private static int create(CommandContext<CommandSourceStack> context, ServerLevel level) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        if (!ZoneManager.isValidId(id)) {
            return idError(source);
        }

        Vec3 pos1 = Vec3Argument.getVec3(context, "pos1");
        Vec3 pos2 = Vec3Argument.getVec3(context, "pos2");

        PlaygroundZone existing = ZoneManager.getZone(id);
        float damageBaseline = existing == null
                ? PlaygroundZone.DEFAULT_DAMAGE_BASELINE
                : existing.getStandardHealth();
        float restoreBaseline = existing == null
                ? PlaygroundZone.DEFAULT_RESTORE_BASELINE
                : existing.getRestoreBaseline();
        boolean keepDurability = existing == null || existing.isKeepDurability();
        boolean damageDisabled = existing != null && existing.isDamageDisabled();
        boolean healDisabled = existing != null && existing.isHealDisabled();

        String dimension = level.dimension().location().toString();
        PlaygroundZone zone = new PlaygroundZone(id, dimension,
                pos1.x, pos1.y, pos1.z,
                pos2.x, pos2.y, pos2.z,
                damageBaseline, restoreBaseline, keepDurability, damageDisabled, healDisabled);

        if (!ZoneSnapshots.isWithinVolumeLimit(zone)) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "[MGP] 场地体积过大：%d 个方块，最大允许 %d 个。",
                    zone.getBlockVolume(), MiniGamePlaygroundConfig.getMaxZoneVolume())));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[MGP] 正在生成区域快照..."), false);
        if (!ZoneSnapshots.save(level, zone)) {
            source.sendFailure(Component.literal(
                    "[MGP] 区域快照生成失败，区域未创建/更新。请查看服务器日志。"));
            return 0;
        }

        boolean replaced = ZoneManager.upsert(zone);

        String message = String.format(Locale.ROOT,
                "[MGP] 已%s区域 '%s'，%s，范围: %s，快照已生成。",
                replaced ? "更新" : "创建", zone.getId(), zone.describeSettings(), zone.describeBounds());
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        if (!ZoneManager.remove(id)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }
        ZoneSnapshots.remove(id);
        source.sendSuccess(() -> Component.literal("[MGP] 已删除区域 '" + id + "' 及其快照。"), true);
        return 1;
    }

    private static int list(CommandSourceStack source, ResourceLocation dimensionArgument) {
        String filter = dimensionArgument == null ? null : dimensionArgument.toString();

        List<PlaygroundZone> zones = new ArrayList<>();
        for (PlaygroundZone zone : ZoneManager.getZones()) {
            if (filter == null || filter.equals(zone.getDimension())) {
                zones.add(zone);
            }
        }

        if (zones.isEmpty()) {
            final String filterForMessage = filter;
            source.sendSuccess(() -> Component.literal(filterForMessage == null
                    ? "[MGP] 当前没有任何游戏场地。使用 /mgp create 创建。"
                    : "[MGP] 维度 '" + filterForMessage + "' 下没有任何游戏场地。"), false);
            return 0;
        }

        final String filterForHeader = filter;
        source.sendSuccess(() -> Component.literal(filterForHeader == null
                ? "[MGP] 当前共有 " + zones.size() + " 个游戏场地:"
                : "[MGP] 维度 '" + filterForHeader + "' 下共有 " + zones.size() + " 个游戏场地:"), false);
        for (PlaygroundZone zone : zones) {
            String line = String.format(Locale.ROOT,
                    " - %s | %s | 范围: %s | 快照: %s",
                    zone.getId(), zone.describeSettings(), zone.describeBounds(),
                    ZoneSnapshots.hasSnapshot(zone.getId()) ? "有" : "无");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return zones.size();
    }

    private static int setDamageBaseline(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        double value = DoubleArgumentType.getDouble(context, "value");
        if (!ZoneManager.isValidHealth((float) value)) {
            source.sendFailure(Component.literal("[MGP] 伤害基准必须是大于 0 的有限数值。"));
            return 0;
        }

        if (!ZoneManager.setStandardHealth(id, (float) value)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        String message = String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 的伤害基准已设置为 %.1f（伤害已启用）。", id, value);
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int disableDamage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        if (!ZoneManager.setDamageDisabled(id, true)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "[MGP] 区域 '" + id + "' 的伤害已禁用（安全区，虚空伤害与 /kill 仍生效）。"), true);
        return 1;
    }

    private static int setHealBaseline(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        double value = DoubleArgumentType.getDouble(context, "value");
        if (!ZoneManager.isValidHealth((float) value)) {
            source.sendFailure(Component.literal("[MGP] 恢复基准必须是大于 0 的有限数值。"));
            return 0;
        }

        if (!ZoneManager.setRestoreBaseline(id, (float) value)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        String message = String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 的恢复基准已设置为 %.1f（恢复已启用）。", id, value);
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int disableHeal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        if (!ZoneManager.setHealDisabled(id, true)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "[MGP] 区域 '" + id + "' 的恢复已禁用。"), true);
        return 1;
    }

    private static int setUnbreakable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        boolean unbreakable = BoolArgumentType.getBool(context, "value");
        if (!ZoneManager.setKeepDurability(id, unbreakable)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        String message = unbreakable
                ? "[MGP] 区域 '" + id + "' 已开启耐久保护：不消耗耐久。"
                : "[MGP] 区域 '" + id + "' 已关闭耐久保护：正常消耗耐久。";
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        PlaygroundZone zone = ZoneManager.getZone(id);
        if (zone == null) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        if (!ZoneSnapshots.isWithinVolumeLimit(zone)) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "[MGP] 场地体积过大：%d 个方块，最大允许 %d 个，无法重置。",
                    zone.getBlockVolume(), MiniGamePlaygroundConfig.getMaxZoneVolume())));
            return 0;
        }

        if (!ZoneSnapshots.hasSnapshot(id)) {
            source.sendFailure(Component.literal(
                    "[MGP] 区域 '" + id + "' 没有快照。请重新执行 /mgp create 或使用 /mgp snapshot " + id + "。"));
            return 0;
        }

        ServerLevel level = ZoneSnapshots.resolveLevel(source.getServer(), source.getLevel(), zone);
        if (level == null) {
            source.sendFailure(Component.literal("[MGP] 无法定位区域所在维度，重置失败。"));
            return 0;
        }

        List<ServerPlayer> players = level.getPlayers(player -> zone.contains(player));
        sendTitle(players, Component.literal("场地正在重置中，请勿破坏...").withStyle(ChatFormatting.RED),
                10, 60, 10);

        int clearedBefore = ZoneSnapshots.clearItems(level, zone);
        if (!ZoneSnapshots.restore(level, zone)) {
            sendTitle(players, Component.literal("场地重置失败").withStyle(ChatFormatting.RED),
                    10, 40, 10);
            source.sendFailure(Component.literal("[MGP] 区域 '" + id + "' 重置失败，请查看服务器日志。"));
            return 0;
        }
        int clearedAfter = ZoneSnapshots.clearItems(level, zone);

        sendTitle(players, Component.literal("场地重置完成").withStyle(ChatFormatting.GREEN),
                10, 40, 10);
        String message = String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 已重置，清理 %d 个掉落物。", id, clearedBefore + clearedAfter);
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int snapshot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        PlaygroundZone zone = ZoneManager.getZone(id);
        if (zone == null) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        if (!ZoneSnapshots.isWithinVolumeLimit(zone)) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "[MGP] 场地体积过大：%d 个方块，最大允许 %d 个，无法生成快照。",
                    zone.getBlockVolume(), MiniGamePlaygroundConfig.getMaxZoneVolume())));
            return 0;
        }

        ServerLevel level = ZoneSnapshots.resolveLevel(source.getServer(), source.getLevel(), zone);
        if (level == null) {
            source.sendFailure(Component.literal("[MGP] 无法定位区域所在维度，快照生成失败。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[MGP] 正在为区域 '" + id + "' 生成快照..."), false);
        if (!ZoneSnapshots.save(level, zone)) {
            source.sendFailure(Component.literal("[MGP] 快照生成失败，请查看服务器日志。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "[MGP] 区域 '" + id + "' 的快照已更新为当前地形。"), true);
        return 1;
    }

    private static void sendTitle(List<ServerPlayer> players, Component title, int fadeIn, int stay, int fadeOut) {
        if (players.isEmpty()) {
            return;
        }
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static int damageRate(CommandContext<CommandSourceStack> context, String zoneId)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");

        float standardHealth;
        String zoneDescription;
        if (zoneId == null) {
            standardHealth = PlaygroundZone.DEFAULT_DAMAGE_BASELINE;
            zoneDescription = "未指定场地，使用默认伤害基准";
        } else {
            PlaygroundZone zone = ZoneManager.getZone(zoneId.trim());
            if (zone == null) {
                source.sendFailure(Component.literal("[MGP] 找不到区域 '" + zoneId + "'。"));
                return 0;
            }
            if (zone.isDamageDisabled()) {
                source.sendSuccess(() -> Component.literal(
                        "[调试] 场地 " + zone.getId() + " 的伤害已禁用（安全区）。"), false);
                return 1;
            }
            standardHealth = zone.getStandardHealth();
            zoneDescription = "场地 " + zone.getId() + " 伤害基准";
        }

        float maxHealth = target.getMaxHealth();
        float multiplier = DamageProcessor.calculateMultiplier(maxHealth, standardHealth);
        if (!Float.isFinite(multiplier)) {
            source.sendFailure(Component.literal("[MGP] 无法计算倍率：玩家最大生命值或伤害基准无效。"));
            return 0;
        }

        String message = String.format(Locale.ROOT,
                "[调试] 玩家 %s 当前生命上限: %.1f。%s: %.1f。该玩家在此场地的伤害倍率为: %.2fx",
                target.getName().getString(), maxHealth, zoneDescription, standardHealth, multiplier);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int healRate(CommandContext<CommandSourceStack> context, String zoneId)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");

        float restoreBaseline;
        String zoneDescription;
        if (zoneId == null) {
            restoreBaseline = PlaygroundZone.DEFAULT_RESTORE_BASELINE;
            zoneDescription = "未指定场地，使用默认恢复基准";
        } else {
            PlaygroundZone zone = ZoneManager.getZone(zoneId.trim());
            if (zone == null) {
                source.sendFailure(Component.literal("[MGP] 找不到区域 '" + zoneId + "'。"));
                return 0;
            }
            if (zone.isHealDisabled()) {
                source.sendSuccess(() -> Component.literal(
                        "[调试] 场地 " + zone.getId() + " 的恢复已禁用。"), false);
                return 1;
            }
            restoreBaseline = zone.getRestoreBaseline();
            zoneDescription = "场地 " + zone.getId() + " 恢复基准";
        }

        float maxHealth = target.getMaxHealth();
        float multiplier = HealingProcessor.calculateHealingMultiplier(maxHealth, restoreBaseline);
        if (!Float.isFinite(multiplier)) {
            source.sendFailure(Component.literal("[MGP] 无法计算倍率：玩家最大生命值或恢复基准无效。"));
            return 0;
        }

        String message = String.format(Locale.ROOT,
                "[调试] 玩家 %s 当前生命上限: %.1f。%s: %.1f。该玩家在此场地的恢复倍率为: %.2fx",
                target.getName().getString(), maxHealth, zoneDescription, restoreBaseline, multiplier);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlaygroundZone zone = ZoneManager.getZone(player);

        if (zone == null) {
            source.sendSuccess(() -> Component.literal("[调试] 你当前不在任何游戏场地内。"), false);
            return 0;
        }

        String message = String.format(Locale.ROOT,
                "[调试] 你在游戏场地内！场地ID: %s，%s。",
                zone.getId(), zone.describeSettings());
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        MiniGamePlaygroundConfig.load();
        ZoneManager.load();
        int count = ZoneManager.size();
        long maxVolume = MiniGamePlaygroundConfig.getMaxZoneVolume();
        source.sendSuccess(() -> Component.literal(
                "[MGP] 已重新加载区域配置，共 " + count + " 个区域；体积上限: " + maxVolume + "。"), true);
        return count;
    }

    private static int help(CommandSourceStack source) {
        String[] lines = {
                "[MGP] MiniGamePlayground 指令帮助（主指令: /mgp；兼容别名: /minigameplayground、/pvb、/pvpbalance）",
                "",
                "区域管理:",
                " /mgp create <区域ID> <x1> <y1> <z1> <x2> <y2> <z2>",
                "   创建区域；维度取执行命令时所在的维度。",
                " /mgp create <区域ID> dimension <维度ID> <x1> <y1> <z1> <x2> <y2> <z2>",
                "   在指定维度创建区域，支持模组新增维度。",
                "   新区域默认伤害基准 20.0、恢复基准 20.0、不消耗耐久。",
                "   如果区域ID已存在则更新范围和维度，并保留原有基准、耐久和 disable 设置。",
                " /mgp remove <区域ID>",
                "   删除指定区域及其快照。",
                " /mgp list [维度ID]",
                "   列出全部区域，或只列出指定维度的区域。",
                " /mgp damage <区域ID> <伤害基准|disable>",
                "   设置伤害基准，或禁用区域内所有伤害（安全区）。",
                "   设为数值会重新启用伤害；虚空伤害和 /kill 不受禁用影响。",
                " /mgp heal <区域ID> <恢复基准|disable>",
                "   设置恢复基准，或禁用区域内所有治疗。",
                "   设为数值会重新启用恢复。",
                " /mgp unbreakable <区域ID> <true|false>",
                "   true = 不消耗物品耐久（默认）；false = 正常消耗耐久。",
                " /mgp reset <区域ID>",
                "   清除区域内掉落物，并将地形恢复到创建/最近快照状态。",
                " /mgp snapshot <区域ID>",
                "   用当前地形重新生成该区域的快照。",
                "",
                "调试与维护:",
                " /mgp damage_rate <玩家> [区域ID]",
                "   查看玩家当前生命上限及伤害倍率；不填区域ID时使用默认伤害基准 20.0。",
                " /mgp heal_rate <玩家> [区域ID]",
                "   查看玩家当前生命上限及恢复倍率；不填区域ID时使用默认恢复基准 20.0。",
                " /mgp check",
                "   检查自己当前是否在游戏场地内。",
                " /mgp reload",
                "   从 config/minigameplayground-zones.json 重新加载区域配置。",
                " /mgp help",
                "   显示本帮助。",
                "",
                "计算规则: 伤害/恢复倍率 = 玩家最大生命值 / 对应基准。",
                "          伤害基准使用受击冷却、盔甲、抗性、保护附魔减伤后的数值（吸收前）。",
                "          达到基准时按最大生命值封顶；低于基准时按比例缩放并保留两位小数。",
                "          吸收（黄色爱心）在倍率之后结算，因此达到基准仍可能被吸收挡住。",
                "          生命上限高于基准时增伤/增加治疗，反之减伤/降低治疗。",
                "快照限制: 单个区域最大 " + MiniGamePlaygroundConfig.getMaxZoneVolume() + " 个方块。",
                "提示: 只有攻击者和受击者处于同一个游戏区域时，伤害倍率才会生效。"
        };
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int idError(CommandSourceStack source) {
        source.sendFailure(Component.literal(
                "[MGP] 区域ID只能包含字母、数字、下划线、短横线或点，长度 1-64。"));
        return 0;
    }
}
