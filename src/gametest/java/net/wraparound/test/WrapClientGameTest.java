package net.wraparound.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Shape;
import net.wraparound.Topology;
import net.wraparound.Wrap;
import net.wraparound.Wraparound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WrapClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("wraparound-test");
	private static final int R = 64;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		torus(ctx);
		klein(ctx);
	}

	private static TestSingleplayerContext world(ClientGameTestContext ctx, Shape shape) {
		return ctx.worldBuilder().adjustSettings(state -> {
			state.setWorldType(state.getNormalPresetList().getFirst());
			state.setSeed("wraparound");
			state.getGameRules().set(Wraparound.SHAPE, shape, null);
			state.getGameRules().set(Wraparound.RADIUS, R, null);
		}).create();
	}

	private static void settle(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		sp.getServer().runCommand("time set noon");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("gamerule minecraft:advance_time false");
		sp.getServer().runCommand("gamemode spectator @a");
		ctx.waitTicks(40);
	}

	private static Vec3 clientPos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static Vec3 serverPos(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		LOG.info("PASS: {}", message);
	}

	private static void torus(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = world(ctx, Shape.TORUS)) {
			Topology topology = sp.getServer().computeOnServer(s -> Wrap.of(s.overworld()));
			check(topology.active() && topology.shape() == Shape.TORUS && topology.radius() == R, "server overworld is a torus of radius " + R + ": " + topology);
			Topology clientTopology = ctx.computeOnClient(mc -> Wrap.of(mc.level));
			check(clientTopology.equals(topology), "client knows the topology: " + clientTopology);
			settle(ctx, sp);

			// Look down from high above a corner of the domain: the world should repeat seamlessly.
			sp.getServer().runCommand("tp @p 0 125 0 facing 30 60 30");
			ctx.waitTicks(20);
			ctx.waitTicks(300);
			LOG.info("top-down view: client pos {} pitch {}, server pitch {}, camera entity {}",
				clientPos(ctx), ctx.computeOnClient(mc -> mc.player.getXRot()),
				sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getXRot()),
				ctx.computeOnClient(mc -> String.valueOf(mc.getCameraEntity())));
			LOG.info("top-down chunks: client loaded {}, chunk(0,0) {}, chunk(5,5) {}, server tracked view {}",
				ctx.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount()),
				ctx.computeOnClient(mc -> String.valueOf(mc.level.getChunkSource().getChunk(0, 0, false))),
				ctx.computeOnClient(mc -> String.valueOf(mc.level.getChunkSource().getChunk(5, 5, false))),
				sp.getServer().computeOnServer(s -> String.valueOf(s.getPlayerList().getPlayers().getFirst().getChunkTrackingView())));
			ctx.takeScreenshot("torus-from-above");

			// Look east across the seam at ground level.
			int ground = sp.getServer().computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, R - 8, 0));
			sp.getServer().runCommand("tp @p " + (R - 24) + " " + (ground + 6) + " 0.5 -90 15");
			ctx.waitTicks(120);
			ctx.takeScreenshot("torus-east-seam");

			// Fly east straight through the seam.
			Vec3 before = clientPos(ctx);
			ctx.getInput().holdKeyFor(options -> options.keyUp, 60);
			ctx.waitTicks(10);
			Vec3 after = clientPos(ctx);
			Vec3 server = serverPos(sp);
			LOG.info("client {} -> {}, server now {}", before, after, server);
			check(after.x > R + 1, "client kept walking east past the seam without a jump (x=" + after.x + ")");
			check(topology.inDomain(server.x, server.z), "server moved the player back inside the domain (x=" + server.x + ")");
			check(Math.abs(topology.toDomain(after.x, after.z).x(after.x) - server.x) < 1.5, "client and server agree on where the player is");
			ctx.takeScreenshot("torus-after-crossing");

			// A block placed at the west edge appears just past the east edge for the client.
			BlockPos domainPos = new BlockPos(-R, ground + 3, 0);
			sp.getServer().runCommand("setblock " + domainPos.getX() + " " + domainPos.getY() + " " + domainPos.getZ() + " gold_block");
			ctx.waitTicks(20);
			BlockState copy = ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(R, domainPos.getY(), 0)));
			check(copy.is(Blocks.GOLD_BLOCK), "gold block placed at x=-" + R + " shows up at x=" + R + " on the client: " + copy);

			// Breaking it through its copy breaks the original.
			sp.getServer().runCommand("setblock " + R + " " + domainPos.getY() + " 0 air");
			ctx.waitTicks(10);
			BlockState original = sp.getServer().computeOnServer(s -> s.overworld().getBlockState(domainPos));
			check(original.isAir(), "setblock just past the seam edits the original block: " + original);

			// Terrain lines up across the seam on the server.
			int[] diffs = sp.getServer().computeOnServer(s -> {
				var level = s.overworld();
				int seamMax = 0;
				int seamTotal = 0;
				int innerMax = 0;
				int innerTotal = 0;
				for (int z = -R; z < R; z++) {
					int seam = Math.abs(ground(level, R - 1, z) - ground(level, -R, z));
					int inner = Math.abs(ground(level, 0, z) - ground(level, 1, z));
					seamMax = Math.max(seamMax, seam);
					seamTotal += seam;
					innerMax = Math.max(innerMax, inner);
					innerTotal += inner;
				}

				return new int[] {seamMax, seamTotal, innerMax, innerTotal};
			});
			LOG.info("height steps across the x seam: max {}, total {}; between two inner columns: max {}, total {}", diffs[0], diffs[1], diffs[2], diffs[3]);
			String periodic = sp.getServer().computeOnServer(s -> {
				var level = s.overworld();
				var randomState = level.getChunkSource().randomState();
				var settings = ((net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) level.getChunkSource().getGenerator()).generatorSettings().value();
				var density = settings.noiseRouter().finalDensity();
				int densityMismatches = 0;
				float worstDensity = 0;
				int groundMismatches = 0;
				for (int z = -R; z < R; z += 2) {
					for (int y = 40; y < 120; y += 4) {
						float a = randomState.sampleBlockValueUncached(density, R, y, z);
						float b = randomState.sampleBlockValueUncached(density, -R, y, z);
						if (Math.abs(a - b) > 1e-4) {
							densityMismatches++;
							worstDensity = Math.max(worstDensity, Math.abs(a - b));
						}
					}

					var outside = level.getChunk(R >> 4, z >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.TERRAIN, true);
					var inside = level.getChunk(-R >> 4, z >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.TERRAIN, true);
					if (ground(outside, z) != ground(inside, z)) {
						groundMismatches++;
					}
				}

				return "density mismatches " + densityMismatches + " (worst " + worstDensity + "), ground mismatches " + groundMismatches + "/" + R;
			});
			LOG.info("column x={} (generated outside) vs x=-{}: {}", R, R, periodic);
			check(diffs[1] <= diffs[3] * 2 + 2 * R / 8, "terrain is as smooth across the seam as between ordinary neighbours (seam total " + diffs[1] + ", inner total " + diffs[3] + ")");

			int loaded = ctx.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount());
			LOG.info("client has {} chunks loaded", loaded);

			mobs(ctx, sp, topology);
		}
	}

	/** Mobs on one side of the seam notice, chase and shoot a player on the other. */
	private static void mobs(ClientGameTestContext ctx, TestSingleplayerContext sp, Topology topology) {
		var server = sp.getServer();
		int y = 150;
		server.runCommand("gamerule minecraft:spawn_mobs false");
		server.runCommand("difficulty normal");
		server.runCommand("time set midnight");
		server.runCommand("kill @e[type=!player]");
		server.runCommand("fill " + (R - 20) + " " + y + " -6 " + (R - 1) + " " + y + " 6 stone");
		server.runCommand("fill " + (-R) + " " + y + " -6 " + (-R + 20) + " " + y + " 6 stone");
		server.runCommand("fill " + (R - 20) + " " + (y + 1) + " -6 " + (R - 1) + " " + (y + 4) + " 6 air");
		server.runCommand("fill " + (-R) + " " + (y + 1) + " -6 " + (-R + 20) + " " + (y + 4) + " 6 air");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a minecraft:resistance infinite 255 true");
		server.runCommand("effect give @a minecraft:regeneration infinite 255 true");
		server.runCommand("tp @p " + (-R + 6.5) + " " + (y + 1) + " 0.5 -90 0");
		ctx.waitTicks(40);

		// A husk 13 blocks away through the seam.
		server.runCommand("summon minecraft:husk " + (R - 7.5) + " " + (y + 1) + " 0.5 {PersistenceRequired:1b}");
		double start = server.computeOnServer(s -> wrappedDistanceToHusk(s, topology));
		ctx.waitTicks(120);
		String target = server.computeOnServer(s -> {
			var husk = s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.HUSK, e -> true).stream().findFirst().orElse(null);
			return husk == null ? "none" : String.valueOf(((net.minecraft.world.entity.Mob) husk).getTarget());
		});
		double end = server.computeOnServer(s -> wrappedDistanceToHusk(s, topology));
		LOG.info("husk target {}, distance through seam {} -> {}", target, start, end);
		check(target.contains("Player"), "husk targets the player across the seam");
		check(end < start - 4, "husk closes in through the seam (" + start + " -> " + end + ")");
		ctx.takeScreenshot("husk-across-seam");
		server.runCommand("kill @e[type=minecraft:husk]");

		// A skeleton across the seam shoots toward the player's nearby copy (east, through the seam).
		server.runCommand("summon minecraft:skeleton " + (R - 5.5) + " " + (y + 1) + " 0.5 {PersistenceRequired:1b}");
		server.runCommand("item replace entity @e[type=minecraft:skeleton] weapon.mainhand with minecraft:bow");
		ctx.waitTicks(60);
		String skeletonInfo = server.computeOnServer(s -> {
			StringBuilder sb = new StringBuilder();
			for (var e : s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.SKELETON, e -> true)) {
				var skeleton = (net.minecraft.world.entity.Mob) e;
				sb.append(skeleton).append(" target=").append(skeleton.getTarget()).append(" hand=").append(skeleton.getMainHandItem())
					.append(" sight=").append(skeleton.getTarget() != null && skeleton.hasLineOfSight(skeleton.getTarget()));
			}

			return sb.toString();
		});
		LOG.info("skeleton: {}", skeletonInfo);
		int waited = server.waitFor(s -> !s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.ARROW, e -> true).isEmpty(), 400);
		double vx = server.computeOnServer(s -> s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.ARROW, e -> true).getFirst().getDeltaMovement().x);
		LOG.info("skeleton shot after {} ticks, arrow vx {}", waited, vx);
		check(vx > 0, "skeleton fires east through the seam toward the player, not west across the world (vx=" + vx + ")");
		server.runCommand("kill @e[type=!player]");
		server.runCommand("gamemode spectator @a");
	}

	private static double wrappedDistanceToHusk(net.minecraft.server.MinecraftServer s, Topology topology) {
		var husk = s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.HUSK, e -> true).stream().findFirst().orElseThrow();
		var player = s.getPlayerList().getPlayers().getFirst();
		return Math.sqrt(topology.distanceSqr(player.getX(), player.getY(), player.getZ(), husk.getX(), husk.getY(), husk.getZ()));
	}

	/** Highest solid terrain block in column (0, z) of the chunk, ignoring trees and plants. */
	private static int ground(net.minecraft.world.level.chunk.ChunkAccess chunk, int z) {
		int x = chunk.getPos().getMinBlockX();
		for (int y = chunk.getMaxY(); y > chunk.getMinY(); y--) {
			BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
			if (!state.isAir() && !state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES)
				&& !state.canBeReplaced() && state.getFluidState().isEmpty()) {
				return y;
			}
		}

		return chunk.getMinY();
	}

	/** Height of solid ground at (x, z) in the level, ignoring trees and plants. */
	private static int ground(net.minecraft.server.level.ServerLevel level, int x, int z) {
		var chunk = level.getChunk(x >> 4, z >> 4);
		int start = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
		for (int y = start; y > level.getMinY(); y--) {
			BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
			if (!state.isAir() && !state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES)
				&& !state.canBeReplaced() && state.getFluidState().isEmpty()) {
				return y;
			}
		}

		return level.getMinY();
	}

	private static void klein(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = world(ctx, Shape.KLEIN_BOTTLE)) {
			Topology topology = sp.getServer().computeOnServer(s -> Wrap.of(s.overworld()));
			check(topology.shape() == Shape.KLEIN_BOTTLE, "server overworld is a Klein bottle");
			settle(ctx, sp);

			// An L made of blocks near the north edge, so the mirror is visible from across the seam.
			int y = sp.getServer().computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 10, -R)) + 2;
			sp.getServer().runCommand("fill 10 " + y + " " + (-R) + " 10 " + (y + 6) + " " + (-R) + " diamond_block");
			sp.getServer().runCommand("fill 10 " + y + " " + (-R) + " 14 " + y + " " + (-R) + " diamond_block");

			// Stand south of the south edge looking south; the L should appear mirrored at x = -11..-15.
			sp.getServer().runCommand("tp @p 0 " + (y + 4) + " " + (R - 12) + " 0 10");
			ctx.waitTicks(160);
			ctx.takeScreenshot("klein-mirrored-L");
			BlockState mirroredFoot = ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(-15, y, R)));
			BlockState mirroredPost = ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(-11, y + 5, R)));
			check(mirroredFoot.is(Blocks.DIAMOND_BLOCK) && mirroredPost.is(Blocks.DIAMOND_BLOCK), "the L shows up mirrored across the north-south seam");

			// Fly south through the flipped seam; the player comes out mirrored on the server.
			Vec3 before = clientPos(ctx);
			ctx.getInput().holdKeyFor(options -> options.keyUp, 60);
			ctx.waitTicks(10);
			Vec3 after = clientPos(ctx);
			Vec3 server = serverPos(sp);
			float serverYaw = sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getYRot());
			LOG.info("klein: client {} -> {}, server {} yaw {}", before, after, server, serverYaw);
			check(after.z > R + 1, "client walks straight through the flipped seam (z=" + after.z + ")");
			check(topology.inDomain(server.x, server.z), "server keeps the player inside the domain");
			ctx.takeScreenshot("klein-after-crossing");
		}
	}

	@SuppressWarnings("unused")
	private static ServerPlayer unused(Minecraft mc) {
		return null;
	}
}
