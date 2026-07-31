package com.duperknight.client.reimbursement;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/** A connection-scoped, dimension-aware physical container selection. */
public record ContainerTarget(String dimension, BlockPos position) {
    public ContainerTarget {
        dimension = Objects.requireNonNullElse(dimension, "");
        position = Objects.requireNonNull(position, "position").toImmutable();
    }

    public String displayCoordinates() {
        return "%d, %d, %d".formatted(position.getX(), position.getY(), position.getZ());
    }
}
