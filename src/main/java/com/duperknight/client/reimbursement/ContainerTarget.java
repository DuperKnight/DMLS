package com.duperknight.client.reimbursement;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/** A connection-scoped, dimension-aware physical container selection. */
public record ContainerTarget(String dimension, BlockPos position) {
    public ContainerTarget {
        dimension = Objects.requireNonNullElse(dimension, "");
        position = Objects.requireNonNull(position, "position").immutable();
    }

    public String displayCoordinates() {
        return "%d, %d, %d".formatted(position.getX(), position.getY(), position.getZ());
    }
}
