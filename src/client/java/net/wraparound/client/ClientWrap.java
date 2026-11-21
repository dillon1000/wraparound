package net.wraparound.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Topology;
import net.wraparound.Transform;
import net.wraparound.Wrap;
import org.jspecify.annotations.Nullable;

/**
 * The client lives in the unrolled world. Every chunk the server sends (always inside the fundamental
 * domain) is copied to each place it appears within view, mirrored where the gluing flips, and every
 * block, light and block entity update is applied to all of those copies. Entities and effects are
 * shown at the copy nearest the player.
 */
public final class ClientWrap {
	private static final Map<ResourceKey<Level>, Topology> TOPOLOGIES = new HashMap<>();

	/** Server coordinates of the local player to client coordinates. */
	private static Transform frame = Transform.IDENTITY;

	/** Domain chunks the server has sent and not forgotten. */
	private static final LongOpenHashSet TRACKED = new LongOpenHashSet();

	/** Server chunk data that arrived while none of its copies were in range. */
	private static final Long2ObjectOpenHashMap<Pending> PENDING = new Long2ObjectOpenHashMap<>();

	private record Pending(ClientboundLevelChunkPacketData data, ClientboundLightUpdatePacketData light) {
	}

	public interface CacheState {
		int wraparound$centerX();

		int wraparound$centerZ();

		int wraparound$viewRadius();
	}

	public interface EntityState {
		@Nullable Transform wraparound$transform();

		void wraparound$setTransform(@Nullable Transform transform);
	}

	private ClientWrap() {
	}

	// ---- Topology and frame ----

	public static void setTopology(ResourceKey<Level> dimension, Topology topology) {
		TOPOLOGIES.put(dimension, topology);
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level.dimension() == dimension) {
			((Wrap.Holder) level).wraparound$setTopology(topology);
		}
	}

	public static Topology topologyFor(ResourceKey<Level> dimension) {
		return TOPOLOGIES.getOrDefault(dimension, Topology.FLAT);
	}

	public static void onDisconnect() {
		TOPOLOGIES.clear();
		frame = Transform.IDENTITY;
		resetLevelState();
	}

	public static void resetLevelState() {
		TRACKED.clear();
		PENDING.clear();
	}

	public static Transform frame() {
		return frame;
	}

	public static void setFrame(Transform serverToClient) {
		frame = serverToClient;
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			((EntityState) player).wraparound$setTransform(serverToClient);
		}
	}

	public static void resetFrame() {
		setFrame(Transform.IDENTITY);
	}

	public static Topology topology(@Nullable Level level) {
		return Wrap.of(level);
	}

	public static boolean active(@Nullable Level level) {
		return level != null && Wrap.of(level).active();
	}

	// ---- Ranges ----

	private static CacheState state(ClientLevel level) {
		return (CacheState) level.getChunkSource();
	}

	/** Radius of the square of chunk positions that get copies, around the storage centre. */
	private static int copyRadius(ClientLevel level) {
		return state(level).wraparound$viewRadius() + 1;
	}

	public static void forEachCopyInRange(ClientLevel level, int dcx, int dcz, java.util.function.Consumer<Transform> action) {
		CacheState s = state(level);
		int r = copyRadius(level);
		topology(level).forEachChunkCopy(dcx, dcz, s.wraparound$centerX() - r, s.wraparound$centerZ() - r, s.wraparound$centerX() + r, s.wraparound$centerZ() + r, action);
	}

	/** Calls {@code action} for every loaded copy of the block at server position {@code pos}. */
	public static void forEachBlockCopy(ClientLevel level, BlockPos pos, BiConsumer<Transform, BlockPos> action) {
		Topology topology = topology(level);
		Transform toDomain = topology.toDomainBlock(pos.getX(), pos.getZ());
		BlockPos domain = toDomain.apply(pos);
		forEachCopyInRange(level, domain.getX() >> 4, domain.getZ() >> 4, t -> {
			BlockPos copy = t.apply(domain);
			if (level.getChunkSource().getChunk(copy.getX() >> 4, copy.getZ() >> 4, false) != null) {
				action.accept(t.compose(toDomain), copy);
			}
		});
	}

	// ---- Chunks ----

	public static void onChunk(ClientLevel level, int dcx, int dcz, ClientboundLevelChunkPacketData data, ClientboundLightUpdatePacketData light) {
		long key = ChunkPos.pack(dcx, dcz);
		TRACKED.add(key);
		List<Transform> copies = new ArrayList<>();
		forEachCopyInRange(level, dcx, dcz, copies::add);
		if (copies.isEmpty()) {
			PENDING.put(key, new Pending(data, light));
			return;
		}

		PENDING.remove(key);
		ClientChunkCache cache = level.getChunkSource();
		LevelChunk plain = null;
		for (Transform t : copies) {
			if (!t.mirror()) {
				int cx = t.chunkX(dcx);
				int cz = t.chunkZ(dcz);
				LevelChunk chunk = cache.replaceWithPacketData(cx, cz, data);
				queueLight(level, cx, cz, light, false, false);
				plain = chunk != null ? chunk : plain;
			}
		}

		boolean temp = false;
		for (Transform t : copies) {
			if (t.mirror()) {
				if (plain == null) {
					plain = new LevelChunk(level, new ChunkPos(dcx, dcz));
					plain.replaceWithPacketData(dcx, dcz, data);
					temp = true;
				}

				int cx = t.chunkX(dcx);
				int cz = t.chunkZ(dcz);
				insert(level, cx, cz, ChunkCopier.mirrored(plain, new ChunkPos(cx, cz)));
				queueLight(level, cx, cz, light, true, false);
			}
		}

		if (temp) {
			plain.clearAllBlockEntities();
		}
	}

	private static void insert(ClientLevel level, int cx, int cz, ChunkCopier.Copy copy) {
		LevelChunk chunk = level.getChunkSource().replaceWithPacketData(cx, cz, copy.data());
		if (chunk != null) {
			copy.apply(chunk);
		}
	}

	public static void onForget(ClientLevel level, ChunkPos pos) {
		TRACKED.remove(pos.pack());
		PENDING.remove(pos.pack());
		forEachCopyInRange(level, pos.x(), pos.z(), t -> drop(level, new ChunkPos(t.chunkX(pos.x()), t.chunkZ(pos.z()))));
	}

	private static void drop(ClientLevel level, ChunkPos pos) {
		level.getChunkSource().drop(pos);
		level.queueLightUpdate(() -> {
			LevelLightEngine engine = level.getLightEngine();
			engine.setLightEnabled(pos, false);
			for (int sy = engine.getMinLightSection(); sy < engine.getMaxLightSection(); sy++) {
				SectionPos sectionPos = SectionPos.of(pos, sy);
				engine.queueSectionData(LightLayer.BLOCK, sectionPos, null);
				engine.queueSectionData(LightLayer.SKY, sectionPos, null);
			}

			for (int sy = level.getMinSectionY(); sy <= level.getMaxSectionY(); sy++) {
				engine.updateSectionStatus(SectionPos.of(pos, sy), true);
			}
		});
	}

	public static void onLight(ClientLevel level, int dcx, int dcz, ClientboundLightUpdatePacketData light) {
		forEachCopyInRange(level, dcx, dcz, t -> queueLight(level, t.chunkX(dcx), t.chunkZ(dcz), light, t.mirror(), true));
	}

	private static void queueLight(ClientLevel level, int cx, int cz, ClientboundLightUpdatePacketData light, boolean mirror, boolean rebuild) {
		level.queueLightUpdate(() -> {
			applyLight(level, cx, cz, light, mirror, rebuild);
			if (!rebuild) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
				if (chunk != null) {
					enableChunkLight(level, chunk);
				}
			}
		});
	}

	private static void applyLight(ClientLevel level, int cx, int cz, ClientboundLightUpdatePacketData light, boolean mirror, boolean rebuild) {
		LevelLightEngine engine = level.getChunkSource().getLightEngine();
		readSections(level, engine, cx, cz, LightLayer.SKY, light.skyYMask(), light.emptySkyYMask(), light.skyUpdates().iterator(), mirror, rebuild);
		readSections(level, engine, cx, cz, LightLayer.BLOCK, light.blockYMask(), light.emptyBlockYMask(), light.blockUpdates().iterator(), mirror, rebuild);
		engine.setLightEnabled(new ChunkPos(cx, cz), true);
	}

	private static void readSections(
		ClientLevel level,
		LevelLightEngine engine,
		int cx,
		int cz,
		LightLayer layer,
		BitSet mask,
		BitSet emptyMask,
		Iterator<byte[]> updates,
		boolean mirror,
		boolean rebuild
	) {
		for (int i = 0; i < engine.getLightSectionCount(); i++) {
			int sy = engine.getMinLightSection() + i;
			boolean haveData = mask.get(i);
			boolean haveEmpty = emptyMask.get(i);
			if (haveData || haveEmpty) {
				DataLayer data = haveData ? new DataLayer(updates.next().clone()) : new DataLayer();
				if (mirror && haveData) {
					data = ChunkCopier.mirror(data);
				}

				engine.queueSectionData(layer, SectionPos.of(cx, sy, cz), data);
				if (rebuild) {
					level.setSectionDirtyWithNeighbors(cx, sy, cz);
				}
			}
		}
	}

	private static void enableChunkLight(ClientLevel level, LevelChunk chunk) {
		LevelLightEngine engine = level.getChunkSource().getLightEngine();
		LevelChunkSection[] sections = chunk.getSections();
		ChunkPos pos = chunk.getPos();
		for (int i = 0; i < sections.length; i++) {
			engine.updateSectionStatus(SectionPos.of(pos, level.getSectionYFromSectionIndex(i)), sections[i].hasOnlyAir());
		}

		level.setSectionRangeDirty(pos.x() - 1, level.getMinSectionY(), pos.z() - 1, pos.x() + 1, level.getMaxSectionY(), pos.z() + 1);
	}

	/** Work to do once the storage centre has moved. */
	public record Recenter(List<Runnable> inserts) {
		public void run() {
			inserts.forEach(Runnable::run);
		}
	}

	/**
	 * Called before the chunk storage centre moves. Snapshots copies for positions coming into range
	 * (from copies already loaded) and drops copies leaving it.
	 */
	public static @Nullable Recenter beforeRecenter(ClientLevel level, int newX, int newZ, int newViewRadius) {
		CacheState s = state(level);
		int oldX = s.wraparound$centerX();
		int oldZ = s.wraparound$centerZ();
		int r = copyRadius(level);
		int nr = newViewRadius + 1;
		if (oldX == newX && oldZ == newZ && r == nr) {
			return null;
		}

		Topology topology = topology(level);
		ClientChunkCache cache = level.getChunkSource();
		LevelLightEngine engine = cache.getLightEngine();
		List<Runnable> inserts = new ArrayList<>();

		for (int x = newX - nr; x <= newX + nr; x++) {
			for (int z = newZ - nr; z <= newZ + nr; z++) {
				boolean wasInRange = Math.abs(x - oldX) <= r && Math.abs(z - oldZ) <= r;
				if (wasInRange && cache.getChunk(x, z, false) != null) {
					continue;
				}

				Transform toDomain = topology.toDomainChunk(x, z);
				int dcx = toDomain.chunkX(x);
				int dcz = toDomain.chunkZ(z);
				long key = ChunkPos.pack(dcx, dcz);
				if (!TRACKED.contains(key)) {
					continue;
				}

				Transform target = toDomain.inverse();
				int tx = x;
				int tz = z;
				LevelChunk[] source = new LevelChunk[1];
				Transform[] sourceTransform = new Transform[1];
				topology.forEachChunkCopy(dcx, dcz, oldX - r, oldZ - r, oldX + r, oldZ + r, t -> {
					if (source[0] == null) {
						LevelChunk chunk = cache.getChunk(t.chunkX(dcx), t.chunkZ(dcz), false);
						if (chunk != null) {
							source[0] = chunk;
							sourceTransform[0] = t;
						}
					}
				});

				if (source[0] != null) {
					boolean mirror = sourceTransform[0].mirror() != target.mirror();
					ChunkCopier.Copy copy = mirror ? ChunkCopier.mirrored(source[0], new ChunkPos(tx, tz)) : ChunkCopier.plain(source[0]);
					ClientboundLightUpdatePacketData light = new ClientboundLightUpdatePacketData(source[0].getPos(), engine, null, null);
					inserts.add(() -> {
						insert(level, tx, tz, copy);
						queueLight(level, tx, tz, light, mirror, false);
					});
				} else {
					Pending pending = PENDING.get(key);
					if (pending != null) {
						inserts.add(() -> insertFromServerData(level, tx, tz, dcx, dcz, pending, target.mirror()));
					}
				}
			}
		}

		for (int x = oldX - r; x <= oldX + r; x++) {
			for (int z = oldZ - r; z <= oldZ + r; z++) {
				if (Math.abs(x - newX) > nr || Math.abs(z - newZ) > nr) {
					if (cache.getChunk(x, z, false) != null) {
						drop(level, new ChunkPos(x, z));
					}
				}
			}
		}

		return new Recenter(inserts);
	}

	private static void insertFromServerData(ClientLevel level, int x, int z, int dcx, int dcz, Pending pending, boolean mirror) {
		PENDING.remove(ChunkPos.pack(dcx, dcz));
		if (!mirror) {
			level.getChunkSource().replaceWithPacketData(x, z, pending.data);
		} else {
			LevelChunk temp = new LevelChunk(level, new ChunkPos(dcx, dcz));
			temp.replaceWithPacketData(dcx, dcz, pending.data);
			insert(level, x, z, ChunkCopier.mirrored(temp, new ChunkPos(x, z)));
			temp.clearAllBlockEntities();
		}

		queueLight(level, x, z, pending.light, mirror, false);
	}

	/** Keeps the chunk storage centred on the local player's unrolled position. */
	public static void followPlayer(ClientLevel level) {
		Player player = Minecraft.getInstance().player;
		if (player == null || player.level() != level || !active(level)) {
			return;
		}

		CacheState s = state(level);
		int cx = SectionPos.blockToSectionCoord(player.getX());
		int cz = SectionPos.blockToSectionCoord(player.getZ());
		if (cx != s.wraparound$centerX() || cz != s.wraparound$centerZ()) {
			level.getChunkSource().updateViewCenter(cx, cz);
		}
	}

	// ---- Entities and effects ----

	public static @Nullable Vec3 playerPos() {
		Player player = Minecraft.getInstance().player;
		return player == null ? null : player.position();
	}

	/** The copy of a server position nearest the local player, as a transform. */
	public static Transform nearPlayer(Level level, double x, double z) {
		Vec3 p = playerPos();
		if (p == null) {
			return frame;
		}

		return topology(level).nearest(x, z, p.x, p.z);
	}

	public static Vec3 nearPlayer(Level level, Vec3 pos) {
		return nearPlayer(level, pos.x, pos.z).apply(pos);
	}

	public static BlockPos nearPlayer(Level level, BlockPos pos) {
		return nearPlayer(level, pos.getX() + 0.5, pos.getZ() + 0.5).apply(pos);
	}

	public static @Nullable Transform stored(Entity entity) {
		return ((EntityState) entity).wraparound$transform();
	}

	/**
	 * The server-to-client transform for an entity whose server position is {@code serverPos}: the copy
	 * closest to where the client already shows it, or to the player for a new entity.
	 */
	public static Transform transformFor(Entity entity, Vec3 serverPos) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player) {
			return frame;
		}

		Topology topology = topology(entity.level());
		Transform current = stored(entity);
		Vec3 reference = current != null ? entity.position() : playerPos();
		if (reference == null) {
			return frame;
		}

		Transform t = topology.nearest(serverPos.x, serverPos.z, reference.x, reference.z);
		((EntityState) entity).wraparound$setTransform(t);
		return t;
	}

	public static Transform currentTransform(Entity entity) {
		Transform t = stored(entity);
		if (t != null) {
			return t;
		}

		return transformFor(entity, entity.getPositionCodec().getBase());
	}

	/** Moves the entity's client-side state by {@code delta} (applied in client space). */
	public static void shift(Entity entity, Transform delta) {
		Vec3 pos = delta.apply(entity.position());
		if (entity.isInterpolating()) {
			entity.getInterpolation().cancel();
		}

		entity.snapTo(pos.x, pos.y, pos.z, delta.yaw(entity.getYRot()), entity.getXRot());
		entity.setDeltaMovement(delta.applyVector(entity.getDeltaMovement()));
		if (entity instanceof LivingEntity living) {
			living.setYHeadRot(delta.yaw(living.getYHeadRot()));
			living.yHeadRotO = living.getYHeadRot();
			living.setYBodyRot(delta.yaw(living.yBodyRot));
			living.yBodyRotO = living.yBodyRot;
		}
	}

	/** Entities left far behind as the player walks around the world reappear at the nearer copy. */
	public static void reanchorEntities(ClientLevel level) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || !active(level)) {
			return;
		}

		Topology topology = topology(level);
		int half = topology.radius();
		Entity vehicle = player.getRootVehicle();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == player || entity == vehicle || entity.getRootVehicle() == vehicle || entity.isPassenger()) {
				continue;
			}

			double dx = Math.abs(entity.getX() - player.getX());
			double dz = Math.abs(entity.getZ() - player.getZ());
			if (dx <= half && (!topology.shape().wrapsZ() || dz <= half)) {
				continue;
			}

			Vec3 serverPos = entity.getPositionCodec().getBase();
			Transform current = currentTransform(entity);
			Transform best = topology.nearest(serverPos.x, serverPos.z, player.getX(), player.getZ());
			if (!best.equals(current)) {
				Transform delta = best.compose(current.inverse());
				shift(entity, delta);
				for (Entity passenger : entity.getIndirectPassengers()) {
					shift(passenger, delta);
					((EntityState) passenger).wraparound$setTransform(best);
				}

				((EntityState) entity).wraparound$setTransform(best);
			}
		}
	}
}
