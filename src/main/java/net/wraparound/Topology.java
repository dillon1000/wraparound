package net.wraparound;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape of a wrapped dimension. The fundamental domain is the square of blocks
 * {@code -radius <= x < radius} and {@code -radius <= z < radius} (unbounded in z for a cylinder).
 * Everything outside it is a copy of something inside it, related by a {@link Transform}.
 */
public final class Topology {
	public static final Topology FLAT = new Topology(Shape.FLAT, 0);

	private final Shape shape;
	private final int radius;
	private final int period;
	private final List<Transform> neighbors;

	public Topology(Shape shape, int radius) {
		this.shape = radius <= 0 ? Shape.FLAT : shape;
		this.radius = this.shape == Shape.FLAT ? 0 : radius;
		this.period = this.radius * 2;
		this.neighbors = buildNeighbors();
	}

	public Shape shape() {
		return shape;
	}

	public int radius() {
		return radius;
	}

	public int period() {
		return period;
	}

	public boolean active() {
		return shape != Shape.FLAT;
	}

	private List<Transform> buildNeighbors() {
		List<Transform> list = new ArrayList<>();
		if (!active()) {
			list.add(Transform.IDENTITY);
			return list;
		}

		int[] zs = shape.wrapsZ() ? new int[] {0, -period, period} : new int[] {0};
		for (int tz : zs) {
			boolean mirror = shape.mirrorsAcrossZ() && tz != 0;
			for (int tx : new int[] {0, -period, period}) {
				list.add(new Transform(mirror, tx, tz));
			}
		}

		return List.copyOf(list);
	}

	// ---- Domain membership ----

	public boolean inDomain(double x, double z) {
		if (!active()) {
			return true;
		}

		return x >= -radius && x < radius && (!shape.wrapsZ() || z >= -radius && z < radius);
	}

	public boolean inDomainBlock(int x, int z) {
		if (!active()) {
			return true;
		}

		return x >= -radius && x < radius && (!shape.wrapsZ() || z >= -radius && z < radius);
	}

	public boolean inDomain(BlockPos pos) {
		return inDomainBlock(pos.getX(), pos.getZ());
	}

	public boolean inDomainChunk(int cx, int cz) {
		return inDomainBlock(cx << 4, cz << 4);
	}

	// ---- Mapping into the domain ----

	/** The transform that carries the point (x, z) into the fundamental domain. */
	public Transform toDomain(double x, double z) {
		if (!active()) {
			return Transform.IDENTITY;
		}

		int kz = shape.wrapsZ() ? Mth.floor((z + radius) / period) : 0;
		boolean mirror = shape.mirrorsAcrossZ() && (kz & 1) != 0;
		double mx = mirror ? -x : x;
		int kx = Mth.floor((mx + radius) / period);
		return new Transform(mirror, -kx * period, -kz * period);
	}

	public Transform toDomainBlock(int x, int z) {
		return toDomain(x + 0.5, z + 0.5);
	}

	public Transform toDomainChunk(int cx, int cz) {
		return toDomain((cx << 4) + 8, (cz << 4) + 8);
	}

	public Transform toDomain(Vec3 pos) {
		return toDomain(pos.x, pos.z);
	}

	public BlockPos wrap(BlockPos pos) {
		return toDomainBlock(pos.getX(), pos.getZ()).apply(pos);
	}

	// ---- Nearest copies ----

	/**
	 * The transform T that makes T(p) the copy of p closest to q (horizontally). Neither point needs
	 * to be inside the domain.
	 */
	public Transform nearest(double px, double pz, double qx, double qz) {
		if (!active()) {
			return Transform.IDENTITY;
		}

		Transform wp = toDomain(px, pz);
		Transform wq = toDomain(qx, qz);
		double dpx = wp.x(px);
		double dpz = wp.z(pz);
		double dqx = wq.x(qx);
		double dqz = wq.z(qz);
		Transform best = Transform.IDENTITY;
		double bestDist = Double.MAX_VALUE;
		for (Transform n : neighbors) {
			double dx = n.x(dpx) - dqx;
			double dz = n.z(dpz) - dqz;
			double d = dx * dx + dz * dz;
			if (d < bestDist - 1.0E-9) {
				bestDist = d;
				best = n;
			}
		}

		return wq.inverse().compose(best).compose(wp);
	}

	public Transform nearest(Vec3 p, Vec3 q) {
		return nearest(p.x, p.z, q.x, q.z);
	}

	public Transform nearestBlock(BlockPos p, double qx, double qz) {
		return nearest(p.getX() + 0.5, p.getZ() + 0.5, qx, qz);
	}

	/** Squared distance from p to the closest copy of q. */
	public double distanceSqr(double px, double py, double pz, double qx, double qy, double qz) {
		Transform t = nearest(qx, qz, px, pz);
		double dx = t.x(qx) - px;
		double dy = qy - py;
		double dz = t.z(qz) - pz;
		return dx * dx + dy * dy + dz * dz;
	}

	/** The closest copy of q to p. */
	public Vec3 nearestCopy(Vec3 q, Vec3 p) {
		return nearest(q, p).apply(q);
	}

	/**
	 * Calls {@code action} with every transform T where T maps the domain chunk (dcx, dcz) into the
	 * chunk rectangle [minX, maxX] × [minZ, maxZ].
	 */
	public void forEachChunkCopy(int dcx, int dcz, int minX, int minZ, int maxX, int maxZ, Consumer<Transform> action) {
		if (!active()) {
			if (dcx >= minX && dcx <= maxX && dcz >= minZ && dcz <= maxZ) {
				action.accept(Transform.IDENTITY);
			}
			return;
		}

		int pc = period >> 4;
		int kzMin = shape.wrapsZ() ? Math.floorDiv(minZ - dcz, pc) - 1 : 0;
		int kzMax = shape.wrapsZ() ? Math.floorDiv(maxZ - dcz, pc) + 1 : 0;
		for (int kz = kzMin; kz <= kzMax; kz++) {
			int cz = dcz + kz * pc;
			if (cz < minZ || cz > maxZ) {
				continue;
			}

			boolean mirror = shape.mirrorsAcrossZ() && (kz & 1) != 0;
			int baseX = mirror ? -1 - dcx : dcx;
			int kxMin = Math.floorDiv(minX - baseX, pc) - 1;
			int kxMax = Math.floorDiv(maxX - baseX, pc) + 1;
			for (int kx = kxMin; kx <= kxMax; kx++) {
				int cx = baseX + kx * pc;
				if (cx >= minX && cx <= maxX) {
					action.accept(new Transform(mirror, kx * period, kz * period));
				}
			}
		}
	}

	/**
	 * Calls {@code action} with every other copy of {@code box} that overlaps the domain. Together with
	 * the box itself these cover everything the box touches in the glued world.
	 */
	public void forEachOtherDomainCopy(net.minecraft.world.phys.AABB box, Consumer<net.minecraft.world.phys.AABB> action) {
		if (!active()) {
			return;
		}

		var center = box.getCenter();
		Transform toDomain = toDomain(center.x, center.z);
		for (Transform n : neighbors) {
			Transform t = n.compose(toDomain);
			if (t.isIdentity()) {
				continue;
			}

			net.minecraft.world.phys.AABB copy = t.apply(box);
			boolean overlapsX = copy.maxX >= -radius && copy.minX < radius;
			boolean overlapsZ = !shape.wrapsZ() || copy.maxZ >= -radius && copy.minZ < radius;
			if (overlapsX && overlapsZ) {
				action.accept(copy);
			}
		}
	}

	// ---- Networking ----

	public void write(FriendlyByteBuf buf) {
		buf.writeEnum(shape);
		buf.writeVarInt(radius);
	}

	public static Topology read(FriendlyByteBuf buf) {
		Shape shape = buf.readEnum(Shape.class);
		int radius = buf.readVarInt();
		return new Topology(shape, radius);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Topology t && t.shape == shape && t.radius == radius;
	}

	@Override
	public int hashCode() {
		return shape.hashCode() * 31 + radius;
	}

	@Override
	public String toString() {
		return shape.getSerializedName() + " ±" + radius;
	}
}
