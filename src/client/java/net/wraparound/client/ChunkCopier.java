package net.wraparound.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.wraparound.client.mixin.LevelChunkSectionAccessor;

/** Builds the mirror image of a client chunk, for copies across a Klein bottle's flipped seam. */
public final class ChunkCopier {
	private ChunkCopier() {
	}

	/** Chunk data plus block entity data to load afterwards (positions are absolute in the copy). */
	public record Copy(ClientboundLevelChunkPacketData data, List<BlockEntityTag> blockEntities) {
		public void apply(LevelChunk target) {
			Level level = target.getLevel();
			for (BlockEntityTag tag : blockEntities) {
				BlockEntity be = target.getBlockEntity(tag.pos, LevelChunk.EntityCreationType.IMMEDIATE);
				if (be != null) {
					be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag.tag));
				}
			}
		}
	}

	public record BlockEntityTag(BlockPos pos, CompoundTag tag) {
	}

	public static Copy plain(LevelChunk source) {
		return new Copy(new ClientboundLevelChunkPacketData(source), List.of());
	}

	/** The chunk at {@code target} that is the east-west mirror image of {@code source}. */
	public static Copy mirrored(LevelChunk source, ChunkPos target) {
		Level level = source.getLevel();
		LevelChunk temp = new LevelChunk(level, target);
		LevelChunkSection[] from = source.getSections();
		LevelChunkSection[] to = temp.getSections();
		for (int i = 0; i < from.length && i < to.length; i++) {
			LevelChunkSection src = from[i];
			LevelChunkSection dst = to[i];
			if (!src.hasOnlyAir()) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							BlockState state = src.getBlockState(15 - x, y, z);
							if (!state.isAir()) {
								dst.setBlockState(x, y, z, state.mirror(Mirror.FRONT_BACK), false);
							}
						}
					}
				}
			}

			PalettedContainerRO<Holder<Biome>> srcBiomes = src.getBiomes();
			PalettedContainer<Holder<Biome>> biomes = dst.getBiomes().recreate();
			for (int qy = 0; qy < 4; qy++) {
				for (int qz = 0; qz < 4; qz++) {
					for (int qx = 0; qx < 4; qx++) {
						biomes.getAndSetUnchecked(qx, qy, qz, srcBiomes.get(3 - qx, qy, qz));
					}
				}
			}

			((LevelChunkSectionAccessor) dst).wraparound$setBiomes(biomes);
			dst.recalcBlockCounts();
		}

		EnumSet<Heightmap.Types> types = EnumSet.noneOf(Heightmap.Types.class);
		for (Heightmap.Types type : Heightmap.Types.values()) {
			if (type.sendToClient()) {
				types.add(type);
			}
		}

		Heightmap.primeHeightmaps(temp, types);

		List<BlockEntityTag> tags = new ArrayList<>();
		int baseX = target.getMinBlockX();
		int baseZ = target.getMinBlockZ();
		for (Map.Entry<BlockPos, BlockEntity> entry : source.getBlockEntities().entrySet()) {
			BlockPos pos = entry.getKey();
			int lx = 15 - (pos.getX() & 15);
			int lz = pos.getZ() & 15;
			CompoundTag tag = entry.getValue().saveWithoutMetadata(level.registryAccess());
			tags.add(new BlockEntityTag(new BlockPos(baseX + lx, pos.getY(), baseZ + lz), tag));
		}

		return new Copy(new ClientboundLevelChunkPacketData(temp), tags);
	}

	public static DataLayer mirror(DataLayer layer) {
		DataLayer out = new DataLayer();
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					int v = layer.get(15 - x, y, z);
					if (v != 0) {
						out.set(x, y, z, v);
					}
				}
			}
		}

		return out;
	}
}
