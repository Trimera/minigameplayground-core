package com.minigameplayground.compat;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.minigameplayground.MiniGamePlaygroundMod;
import com.minigameplayground.zone.PlaygroundZone;
import net.minecraft.server.level.ServerLevel;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional WorldEdit-backed snapshot implementation.
 *
 * <p>This class is intentionally isolated and is only loaded through reflection when
 * the {@code worldedit} mod is installed. The base mod therefore has no hard
 * dependency on WorldEdit and can still use its built-in fallback snapshot format.</p>
 */
public final class WorldEditBridge {
    private WorldEditBridge() {
    }

    public static boolean save(ServerLevel level, PlaygroundZone zone, Path file) {
        try {
            World world = FabricAdapter.adapt(level);
            BlockVector3 min = min(zone);
            BlockVector3 max = max(zone);
            CuboidRegion region = new CuboidRegion(min, max);
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(min);

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(world);
                 ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(
                         Files.newOutputStream(file))) {
                ForwardExtentCopy copy = new ForwardExtentCopy(world, region, clipboard, min);
                copy.setCopyingEntities(false);
                copy.setRemovingEntities(false);
                copy.setCopyingBiomes(false);
                Operations.complete(copy);
                writer.write(clipboard);
            }
            return true;
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("WorldEdit snapshot failed for zone '{}'.", zone.getId(), exception);
            return false;
        }
    }

    public static boolean restore(ServerLevel level, PlaygroundZone zone, Path file) {
        try (InputStream input = Files.newInputStream(file);
             ClipboardReader reader = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(input)) {
            Clipboard clipboard = reader.read();
            World world = FabricAdapter.adapt(level);
            BlockVector3 target = min(zone);

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
                Operation paste = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(target)
                        .ignoreAirBlocks(false)
                        .copyEntities(false)
                        .build();
                Operations.complete(paste);
            }
            return true;
        } catch (Exception exception) {
            MiniGamePlaygroundMod.LOGGER.error("WorldEdit restore failed for zone '{}'.", zone.getId(), exception);
            return false;
        }
    }

    private static BlockVector3 min(PlaygroundZone zone) {
        return BlockVector3.at(zone.getMinBlockX(), zone.getMinBlockY(), zone.getMinBlockZ());
    }

    private static BlockVector3 max(PlaygroundZone zone) {
        return BlockVector3.at(zone.getMaxBlockX(), zone.getMaxBlockY(), zone.getMaxBlockZ());
    }
}
