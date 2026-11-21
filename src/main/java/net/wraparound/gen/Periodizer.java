package net.wraparound.gen;

import net.wraparound.Topology;
import net.wraparound.Transform;

/**
 * Turns any field over block coordinates into one that respects the world's gluing, so terrain lines
 * up across every seam.
 *
 * <p>Inside the domain the field is left untouched except in a band next to the positive edge of each
 * wrapped axis. There it cross-fades toward the field just past the negative edge, so by the time it
 * reaches the seam it equals what lies on the other side. For the Klein bottle the north-south
 * cross-fade samples the mirrored column, matching the flip in the gluing.
 */
public final class Periodizer {
	private static final int MAX_BAND = 256;

	@FunctionalInterface
	public interface Field {
		double get(double x, double y, double z);
	}

	private final Topology topology;
	private final int radius;
	private final int period;
	private final int band;
	private final boolean wrapsZ;
	private final boolean mirrorZ;

	public Periodizer(Topology topology) {
		this.topology = topology;
		this.radius = topology.radius();
		this.period = topology.period();
		this.band = Math.max(16, Math.min(MAX_BAND, radius));
		this.wrapsZ = topology.shape().wrapsZ();
		this.mirrorZ = topology.shape().mirrorsAcrossZ();
	}

	public Topology topology() {
		return topology;
	}

	/** Whether the field is left exactly as it is over this block rectangle (inside the domain, away from the bands). */
	public boolean untouched(int minX, int minZ, int maxX, int maxZ) {
		int limit = radius - band;
		if (minX < -radius || maxX >= limit) {
			return false;
		}

		return !wrapsZ || minZ >= -radius && maxZ < limit;
	}

	/** Samples {@code field} at block-space coordinates (x, y, z) through the gluing. */
	public double sample(double x, double y, double z, Field field) {
		Transform toDomain = topology.toDomain(x + 0.5, z + 0.5);
		double dx = toDomain.mirror() ? toDomain.tx() - 1 - x : x + toDomain.tx();
		double dz = z + toDomain.tz();
		if (!wrapsZ) {
			return blendX(dx, y, dz, field);
		}

		double wz = weight(dz);
		double a = wz >= 1.0 ? 0.0 : blendX(dx, y, dz, field);
		if (wz <= 0.0) {
			return a;
		}

		double otherX = mirrorZ ? -1 - dx : dx;
		double b = blendX(otherX, y, dz - period, field);
		return a + (b - a) * wz;
	}

	private double blendX(double x, double y, double z, Field field) {
		double wx = weight(x);
		double a = wx >= 1.0 ? 0.0 : field.get(x, y, z);
		if (wx <= 0.0) {
			return a;
		}

		double b = field.get(x - period, y, z);
		return a + (b - a) * wx;
	}

	/** 0 for most of the domain, rising smoothly to 1 at the positive edge. */
	private double weight(double c) {
		double s = c - (radius - band);
		if (s <= 0.0) {
			return 0.0;
		}

		double t = Math.min(1.0, s / band);
		return t * t * (3.0 - 2.0 * t);
	}
}
