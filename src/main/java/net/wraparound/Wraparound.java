package net.wraparound;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.wraparound.net.FramePayload;
import net.wraparound.net.TopologyPayload;
import net.wraparound.server.ServerWrap;
import org.slf4j.Logger;

public class Wraparound implements ModInitializer {
	public static final String MOD_ID = "wraparound";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final int MIN_RADIUS = 32;
	public static final int MAX_RADIUS = 1 << 20;

	public static final GameRule<Shape> SHAPE = GameRuleBuilder.forEnum(Shape.FLAT)
		.category(GameRuleCategory.MISC)
		.buildAndRegister(id("world_shape"));

	public static final GameRule<Integer> RADIUS = GameRuleBuilder.forInteger(256)
		.category(GameRuleCategory.MISC)
		.range(MIN_RADIUS, MAX_RADIUS)
		.buildAndRegister(id("world_radius"));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * The topology a dimension uses, read from game rules. Only the overworld wraps. The radius is
	 * rounded down to whole chunks.
	 */
	public static Topology topologyFor(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension) {
		if (dimension != Level.OVERWORLD) {
			return Topology.FLAT;
		}

		Shape shape = server.getGameRules().get(SHAPE);
		int radius = Math.max(MIN_RADIUS, server.getGameRules().get(RADIUS)) & ~15;
		return new Topology(shape, radius);
	}

	@Override
	public void onInitialize() {
		// Force the game rule fields to initialize.
		LOGGER.debug("Wraparound rules: {} {}", SHAPE, RADIUS);

		PayloadTypeRegistry.clientboundPlay().register(TopologyPayload.TYPE, TopologyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FramePayload.TYPE, FramePayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			ServerPlayer player = listener.player;
			sendTopologies(player, server);
			ServerWrap.resetFrame(player);
		});

		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> ServerWrap.resetFrame(player));

		ServerWrap.init();
	}

	private static void sendTopologies(ServerPlayer player, MinecraftServer server) {
		if (!ServerPlayNetworking.canSend(player, TopologyPayload.TYPE)) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			Topology topology = Wrap.of(level);
			if (topology.active()) {
				ServerPlayNetworking.send(player, new TopologyPayload(level.dimension(), topology));
			}
		}
	}
}
