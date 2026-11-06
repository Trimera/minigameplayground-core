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
import com.minigameplayground.zone.DamageMode;
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
    private static final int MAX_BATCH_ZONES = 32;
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

    private static final SuggestionProvider<CommandSourceStack> LIST_FILTER_SUGGESTIONS = (context, builder) -> {
        for (ResourceKey<Level> key : context.getSource().getServer().levelKeys()) {
            builder.suggest(key.location().toString());
        }
        for (String id : ZoneManager.getZoneIds()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> DAMAGE_MODE_SUGGESTIONS = (context, builder) -> {
        builder.suggest("pvp");
        builder.suggest("all");
        return builder.buildFuture();
    };

    private PlaygroundCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot("mgp"));
        dispatcher.register(buildRoot("minigameplayground"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .requires(source -> source.hasPermission(OP_LEVEL))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), null))
                        .then(Commands.argument("filter", StringArgumentType.greedyString())
                                .suggests(LIST_FILTER_SUGGESTIONS)
                                .executes(context -> list(context.getSource(),
                                        StringArgumentType.getString(context, "filter")))))
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
        root.then(damageModeCommand());
        root.then(flightCommand());
        root.then(mobDropsCommand());

        root.then(Commands.literal("unbreakable")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(PlaygroundCommand::setUnbreakable))));

        root.then(Commands.literal("reset")
                .then(Commands.argument("pattern", StringArgumentType.greedyString())
                        .suggests(ZONE_SUGGESTIONS)
                        .executes(PlaygroundCommand::reset)));

        root.then(Commands.literal("snapshot")
                .then(Commands.argument("pattern", StringArgumentType.greedyString())
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

    private static LiteralArgumentBuilder<CommandSourceStack> damageModeCommand() {
        return Commands.literal("damage_mode")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .executes(PlaygroundCommand::showDamageMode)
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(DAMAGE_MODE_SUGGESTIONS)
                                .executes(PlaygroundCommand::setDamageMode)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> flightCommand() {
        return Commands.literal("flight")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .executes(PlaygroundCommand::showFlight)
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(PlaygroundCommand::setFlight)));
    }

    private static int showFlight(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        PlaygroundZone zone = ZoneManager.getZone(id);
        if (zone == null) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 的创造飞行: %s（仅影响生存/冒险/极限模式；创造/旁观始终允许）",
                id, zone.isCreativeFlightAllowed() ? "允许" : "禁止")), false);
        return 1;
    }

    private static int setFlight(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        boolean allowed = BoolArgumentType.getBool(context, "value");
        if (!ZoneManager.setAllowCreativeFlight(id, allowed)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 已%s创造飞行（仅影响生存/冒险/极限模式）。",
                id, allowed ? "允许" : "禁止")), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mobDropsCommand() {
        return Commands.literal("mobdrops")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(ZONE_SUGGESTIONS)
                        .executes(PlaygroundCommand::showMobDrops)
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(PlaygroundCommand::setMobDrops)));
    }

    private static int showMobDrops(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        PlaygroundZone zone = ZoneManager.getZone(id);
        if (zone == null) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 的怪物掉落: %s（仅影响区域内 Mob 的物品掉落）",
                id, zone.isMobDropsAllowed() ? "允许" : "禁止")), false);
        return 1;
    }

    private static int setMobDrops(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        boolean allowed = BoolArgumentType.getBool(context, "value");
        if (!ZoneManager.setAllowMobDrops(id, allowed)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 已%s怪物掉落。", id, allowed ? "允许" : "禁止")), true);
        return 1;
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
        DamageMode damageMode = existing == null ? DamageMode.PVP : existing.getDamageMode();
        boolean allowCreativeFlight = existing != null && existing.isCreativeFlightAllowed();
        boolean allowMobDrops = existing != null && existing.isMobDropsAllowed();

        String dimension = level.dimension().location().toString();
        PlaygroundZone zone = new PlaygroundZone(id, dimension,
                pos1.x, pos1.y, pos1.z,
                pos2.x, pos2.y, pos2.z,
                damageBaseline, restoreBaseline, keepDurability, damageDisabled, healDisabled,
                damageMode, allowCreativeFlight, allowMobDrops);

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

    private static int list(CommandSourceStack source, String filterArgument) {
        String filter = filterArgument == null ? null : filterArgument.trim();
        if (filter != null && filter.isEmpty()) {
            filter = null;
        }

        List<PlaygroundZone> zones = new ArrayList<>();
        String description;
        if (filter == null) {
            zones.addAll(ZoneManager.getZones());
            description = null;
        } else if (filter.indexOf(':') >= 0) {
            ResourceLocation dimension = ResourceLocation.tryParse(filter);
            if (dimension == null) {
                final String rawFilter = filter;
                source.sendFailure(Component.literal("[MGP] 维度 ID '" + rawFilter + "' 无效。"));
                return 0;
            }
            String dimensionId = dimension.toString();
            for (PlaygroundZone zone : ZoneManager.getZones()) {
                if (dimensionId.equals(zone.getDimension())) {
                    zones.add(zone);
                }
            }
            description = "维度 '" + dimensionId + "' 下";
        } else {
            zones.addAll(ZoneManager.matchZones(filter));
            description = "区域模式 '" + filter + "' 下";
        }

        if (zones.isEmpty()) {
            final String descriptionForMessage = description;
            source.sendSuccess(() -> Component.literal(descriptionForMessage == null
                    ? "[MGP] 当前没有任何游戏场地。使用 /mgp create 创建。"
                    : "[MGP] " + descriptionForMessage + "没有任何游戏场地。"), false);
            return 0;
        }

        final String descriptionForHeader = description;
        source.sendSuccess(() -> Component.literal(descriptionForHeader == null
                ? "[MGP] 当前共有 " + zones.size() + " 个游戏场地:"
                : "[MGP] " + descriptionForHeader + "共有 " + zones.size() + " 个游戏场地:"), false);
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

    private static int showDamageMode(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        PlaygroundZone zone = ZoneManager.getZone(id);
        if (zone == null) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 当前伤害模式: %s (%s)。",
                zone.getId(), zone.getDamageMode().getDisplayName(),
                zone.getDamageMode().getId())), false);
        return 1;
    }

    private static int setDamageMode(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id").trim();
        String modeId = StringArgumentType.getString(context, "mode").trim();
        DamageMode mode = DamageMode.fromId(modeId);
        if (mode == null) {
            source.sendFailure(Component.literal(
                    "[MGP] 伤害模式必须是 pvp 或 all。"));
            return 0;
        }
        if (!ZoneManager.setDamageMode(id, mode)) {
            source.sendFailure(Component.literal("[MGP] 找不到区域 '" + id + "'。"));
            return 0;
        }

        String message = mode == DamageMode.ALL
                ? "[MGP] 区域 '" + id + "' 已设为 all：区域内玩家受到的所有伤害（除虚空和 /kill）都会应用平衡。"
                : "[MGP] 区域 '" + id + "' 已设为 pvp：只平衡同一区域内的玩家对玩家伤害。";
        source.sendSuccess(() -> Component.literal(message), true);
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
        String pattern = StringArgumentType.getString(context, "pattern").trim();
        List<PlaygroundZone> zones = matchBatchZones(source, pattern, "重置");
        if (zones == null) {
            return 0;
        }

        if (zones.size() > 1) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[MGP] 匹配到 %d 个区域，将按以下顺序重置：", zones.size())), false);
            for (PlaygroundZone zone : zones) {
                String id = zone.getId();
                source.sendSuccess(() -> Component.literal(" - " + id), false);
            }
        }

        int succeeded = 0;
        for (PlaygroundZone zone : zones) {
            if (resetZone(source, zone)) {
                succeeded++;
            }
        }

        int succeededCount = succeeded;
        int failed = zones.size() - succeeded;
        if (zones.size() > 1) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[MGP] 批量重置完成：成功 %d 个，失败 %d 个。", succeededCount, failed)),
                    failed == 0);
        }
        return failed == 0 ? succeeded : 0;
    }

    private static boolean resetZone(CommandSourceStack source, PlaygroundZone zone) {
        String id = zone.getId();
        if (!ZoneSnapshots.isWithinVolumeLimit(zone)) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "[MGP] 区域 '%s' 体积过大：%d 个方块，最大允许 %d 个，无法重置。",
                    id, zone.getBlockVolume(), MiniGamePlaygroundConfig.getMaxZoneVolume())));
            return false;
        }

        if (!ZoneSnapshots.hasSnapshot(id)) {
            source.sendFailure(Component.literal(
                    "[MGP] 区域 '" + id + "' 没有快照。请重新执行 /mgp create 或使用 /mgp snapshot " + id + "。"));
            return false;
        }

        ServerLevel level = ZoneSnapshots.resolveLevel(source.getServer(), source.getLevel(), zone);
        if (level == null) {
            source.sendFailure(Component.literal("[MGP] 区域 '" + id + "' 无法定位所在维度，重置失败。"));
            return false;
        }

        List<ServerPlayer> players = level.getPlayers(player -> zone.contains(player));
        sendTitle(players, Component.literal("场地正在重置中，请勿破坏...").withStyle(ChatFormatting.RED),
                10, 60, 10);

        if (!ZoneSnapshots.restore(level, zone)) {
            sendTitle(players, Component.literal("场地重置失败").withStyle(ChatFormatting.RED),
                    10, 40, 10);
            source.sendFailure(Component.literal("[MGP] 区域 '" + id + "' 重置失败，请查看服务器日志。"));
            return false;
        }

        sendTitle(players, Component.literal("场地重置完成").withStyle(ChatFormatting.GREEN),
                10, 40, 10);
        String message = String.format(Locale.ROOT,
                "[MGP] 区域 '%s' 已恢复方块和快照实体。", id);
        source.sendSuccess(() -> Component.literal(message), true);
        return true;
    }

    private static int snapshot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String pattern = StringArgumentType.getString(context, "pattern").trim();
        List<PlaygroundZone> zones = matchBatchZones(source, pattern, "生成快照");
        if (zones == null) {
            return 0;
        }

        if (zones.size() > 1) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[MGP] 匹配到 %d 个区域，将按以下顺序生成快照：", zones.size())), false);
            for (PlaygroundZone zone : zones) {
                String id = zone.getId();
                source.sendSuccess(() -> Component.literal(" - " + id), false);
            }
        }

        int succeeded = 0;
        for (PlaygroundZone zone : zones) {
            if (snapshotZone(source, zone)) {
                succeeded++;
            }
        }

        int succeededCount = succeeded;
        int failed = zones.size() - succeeded;
        if (zones.size() > 1) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "[MGP] 批量生成快照完成：成功 %d 个，失败 %d 个。", succeededCount, failed)),
                    failed == 0);
        }
        return failed == 0 ? succeeded : 0;
    }

    private static boolean snapshotZone(CommandSourceStack source, PlaygroundZone zone) {
        String id = zone.getId();
        if (!ZoneSnapshots.isWithinVolumeLimit(zone)) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "[MGP] 区域 '%s' 体积过大：%d 个方块，最大允许 %d 个，无法生成快照。",
                    id, zone.getBlockVolume(), MiniGamePlaygroundConfig.getMaxZoneVolume())));
            return false;
        }

        ServerLevel level = ZoneSnapshots.resolveLevel(source.getServer(), source.getLevel(), zone);
        if (level == null) {
            source.sendFailure(Component.literal("[MGP] 区域 '" + id + "' 无法定位所在维度，快照生成失败。"));
            return false;
        }

        source.sendSuccess(() -> Component.literal("[MGP] 正在为区域 '" + id + "' 生成快照..."), false);
        if (!ZoneSnapshots.save(level, zone)) {
            source.sendFailure(Component.literal("[MGP] 区域 '" + id + "' 快照生成失败，请查看服务器日志。"));
            return false;
        }

        source.sendSuccess(() -> Component.literal(
                "[MGP] 区域 '" + id + "' 的快照已更新为当前地形。"), true);
        return true;
    }

    /**
     * Resolves a zone pattern for batch commands. Returns {@code null} after reporting an
     * error when nothing matches or the match exceeds the per-command limit.
     */
    private static List<PlaygroundZone> matchBatchZones(CommandSourceStack source,
                                                        String pattern, String action) {
        List<PlaygroundZone> zones = ZoneManager.matchZones(pattern);
        if (zones.isEmpty()) {
            source.sendFailure(Component.literal(
                    "[MGP] 没有区域匹配 '" + pattern + "'，未执行" + action + "。"));
            return null;
        }
        if (zones.size() > MAX_BATCH_ZONES) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "[MGP] 匹配到 %d 个区域，超过单次上限 %d 个，未执行%s。请缩小匹配范围。",
                    zones.size(), MAX_BATCH_ZONES, action)));
            return null;
        }
        return zones;
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
                "[MGP] MiniGamePlayground 指令帮助（主指令: /mgp；兼容别名: /minigameplayground）",
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
                " /mgp list [维度ID|区域模式]",
                "   列出全部区域；含冒号的参数按维度过滤，否则按区域模式过滤。",
                "   区域模式支持通配符: * 匹配任意字符，? 匹配单个字符。",
                " /mgp damage <区域ID> <伤害基准|disable>",
                "   设置伤害基准，或禁用区域内所有伤害（安全区）。",
                "   设为数值会重新启用伤害；虚空伤害和 /kill 不受禁用影响。",
                " /mgp heal <区域ID> <恢复基准|disable>",
                "   设置恢复基准，或禁用区域内所有治疗。",
                "   设为数值会重新启用恢复。",
                " /mgp damage_mode <区域ID> <pvp|all>",
                "   设置该区域的伤害平衡模式；不填模式时显示当前值。",
                "   pvp = 只平衡同一区域内的玩家对玩家伤害（默认）。",
                "   all = 只要求受害者在区域内；玩家、生物、投射物、无来源、自伤和环境伤害都平衡。",
                "   无论哪种模式，虚空伤害和 /kill 都不受平衡影响。",
                " /mgp flight <区域ID>",
                "   查看该区域是否允许生存/冒险/极限模式玩家创造飞行。",
                " /mgp flight <区域ID> <true|false>",
                "   设置区域创造飞行；默认禁止。创造/旁观模式始终允许。",
                " /mgp mobdrops <区域ID>",
                "   查看区域内 Mob 是否允许生成物品掉落。",
                " /mgp mobdrops <区域ID> <true|false>",
                "   设置区域怪物掉落；默认禁止。",
                " /mgp unbreakable <区域ID> <true|false>",
                "   true = 不消耗物品耐久（默认）；false = 正常消耗耐久。",
                " /mgp reset <区域ID|区域模式>",
                "   清除区域内非玩家实体，恢复方块、方块实体和快照实体。",
                "   支持通配符批量处理；按区域创建顺序执行，单次最多 " + MAX_BATCH_ZONES + " 个。",
                " /mgp snapshot <区域ID|区域模式>",
                "   保存当前地形和实体，作为该区域的新快照。",
                "   支持通配符批量处理；按区域创建顺序执行，单次最多 " + MAX_BATCH_ZONES + " 个。",
                "",
                "调试与维护:",
                " /mgp damage_rate <玩家> [区域ID]",
                "   查看玩家当前生命上限及伤害倍率；不填区域ID时使用默认伤害基准 20.0。",
                " /mgp heal_rate <玩家> [区域ID]",
                "   查看玩家当前生命上限及恢复倍率；不填区域ID时使用默认恢复基准 20.0。",
                " /mgp check",
                "   检查自己当前是否在游戏场地内。",
                " /mgp reload",
                "   从当前存档的 minigameplayground/zones.json 重新加载区域配置。",
                " /mgp help",
                "   显示本帮助。",
                "",
                "计算规则: 伤害/恢复倍率 = 玩家最大生命值 / 对应基准。",
                "          伤害基准使用受击冷却、盔甲、抗性、保护附魔减伤后的数值（吸收前）。",
                "          达到基准时按最大生命值封顶；低于基准时按比例缩放并保留两位小数。",
                "          吸收（黄色爱心）在倍率之后结算，因此达到基准仍可能被吸收挡住。",
                "          生命上限高于基准时增伤/增加治疗，反之减伤/降低治疗。",
                "快照限制: 单个区域最大 " + MiniGamePlaygroundConfig.getMaxZoneVolume() + " 个方块。",
                "提示: pvp 模式要求攻击者和受击者在同一区域；all 模式只要求受击者在区域内。"
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
