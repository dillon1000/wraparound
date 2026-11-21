package net.wraparound.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.wraparound.net.FramePayload;
import net.wraparound.net.TopologyPayload;

public class WraparoundClient implements ClientModInitializer {
	private int ticks;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(TopologyPayload.TYPE, (payload, context) -> ClientWrap.setTopology(payload.dimension(), payload.topology()));
		ClientPlayNetworking.registerGlobalReceiver(FramePayload.TYPE, (payload, context) -> ClientWrap.setFrame(payload.serverToClient()));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientWrap.onDisconnect());

		ClientTickEvents.END_LEVEL_TICK.register(level -> {
			ClientWrap.followPlayer(level);
			if (++ticks % 10 == 0) {
				ClientWrap.reanchorEntities(level);
			}
		});
	}
}
