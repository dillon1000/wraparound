package net.wraparound.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Topology;
import net.wraparound.Transform;
import net.wraparound.Wrap;

/**
 * Where things are as seen by one entity on the server: the copy of a position or entity closest to
 * the observer. Mob AI uses this so it aims, walks and swings toward targets across a seam.
 */
public final class Relative {
	private Relative() {
	}

	private static boolean wraps(Entity observer) {
		return Wrap.server(observer.level());
	}

	public static Transform toward(Entity observer, double x, double z) {
		return Wrap.of(observer.level()).nearest(x, z, observer.getX(), observer.getZ());
	}

	public static double x(Entity other, Entity observer) {
		if (other == observer || !wraps(observer)) {
			return other.getX();
		}

		return toward(observer, other.getX(), other.getZ()).x(other.getX());
	}

	public static double z(Entity other, Entity observer) {
		if (other == observer || !wraps(observer)) {
			return other.getZ();
		}

		return toward(observer, other.getX(), other.getZ()).z(other.getZ());
	}

	public static Vec3 pos(Vec3 pos, Entity observer) {
		return wraps(observer) ? toward(observer, pos.x, pos.z).apply(pos) : pos;
	}

	public static AABB box(AABB box, Entity observer) {
		if (!wraps(observer)) {
			return box;
		}

		Vec3 c = box.getCenter();
		return toward(observer, c.x, c.z).apply(box);
	}

	public static double distanceSqr(Entity observer, double x, double y, double z) {
		Topology topology = Wrap.of(observer.level());
		return topology.distanceSqr(observer.getX(), observer.getY(), observer.getZ(), x, y, z);
	}
}
