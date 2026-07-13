package com.duperknight.client.session;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/** A bounded, single-use confirmation token around a prepared immutable request. */
public final class PendingConfirmation<T> {
    public static final int DEFAULT_EXPIRY_TICKS = 600;
    public static final Duration DEFAULT_EXPIRY = Duration.ofSeconds(30);

    public enum ConsumeStatus {
        CONFIRMED,
        INVALID_TOKEN,
        EXPIRED,
        ALREADY_CONSUMED,
        INVALIDATED
    }

    public record ConsumeResult<T>(ConsumeStatus status, Optional<T> request) {
        public ConsumeResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(request, "request");
        }
    }

    private enum State { ACTIVE, EXPIRED, CONSUMED, INVALIDATED }

    private final T request;
    private final String token;
    private final int expiryTicks;
    private final long expiryNanos;
    private final LongSupplier nanoTime;
    private final long createdAtNanos;

    private int elapsedTicks;
    private State state = State.ACTIVE;

    public PendingConfirmation(T request) {
        this(request, DEFAULT_EXPIRY_TICKS, DEFAULT_EXPIRY, System::nanoTime);
    }

    public PendingConfirmation(
            T request,
            int expiryTicks,
            Duration expiry,
            LongSupplier nanoTime
    ) {
        this.request = Objects.requireNonNull(request, "request");
        if (expiryTicks < 1) throw new IllegalArgumentException("expiryTicks");
        Objects.requireNonNull(expiry, "expiry");
        if (expiry.isZero() || expiry.isNegative()) throw new IllegalArgumentException("expiry");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.expiryTicks = expiryTicks;
        this.expiryNanos = expiry.toNanos();
        this.createdAtNanos = nanoTime.getAsLong();
        this.token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public String token() {
        return token;
    }

    /** The frozen request for review display; execution must use the value returned by consume. */
    public T request() {
        return request;
    }

    public synchronized boolean tick() {
        if (state == State.ACTIVE && ++elapsedTicks >= expiryTicks) state = State.EXPIRED;
        expireByWallClock();
        return state == State.ACTIVE;
    }

    public synchronized boolean isActive() {
        expireByWallClock();
        return state == State.ACTIVE;
    }

    public synchronized void invalidate() {
        if (state == State.ACTIVE) state = State.INVALIDATED;
    }

    public synchronized ConsumeResult<T> consume(String candidateToken) {
        expireByWallClock();
        if (state == State.EXPIRED) return result(ConsumeStatus.EXPIRED, Optional.empty());
        if (state == State.CONSUMED) return result(ConsumeStatus.ALREADY_CONSUMED, Optional.empty());
        if (state == State.INVALIDATED) return result(ConsumeStatus.INVALIDATED, Optional.empty());
        if (!token.equals(candidateToken)) return result(ConsumeStatus.INVALID_TOKEN, Optional.empty());

        state = State.CONSUMED;
        return result(ConsumeStatus.CONFIRMED, Optional.of(request));
    }

    private void expireByWallClock() {
        if (state == State.ACTIVE && nanoTime.getAsLong() - createdAtNanos >= expiryNanos) {
            state = State.EXPIRED;
        }
    }

    private ConsumeResult<T> result(ConsumeStatus status, Optional<T> value) {
        return new ConsumeResult<>(status, value);
    }
}
