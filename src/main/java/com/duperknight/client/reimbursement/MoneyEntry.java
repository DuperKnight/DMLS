package com.duperknight.client.reimbursement;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable economy reimbursement request. */
public record MoneyEntry(
        BigDecimal amount,
        Destination destination,
        String playerIgn
) implements ReimbursementEntry {
    public MoneyEntry {
        amount = amount == null ? BigDecimal.ZERO : amount;
        destination = destination == Destination.PLAYER ? Destination.PLAYER : Destination.ME;
        playerIgn = Objects.requireNonNullElse(playerIgn, "");
    }

    public static MoneyEntry empty() {
        return new MoneyEntry(BigDecimal.ZERO, Destination.ME, "");
    }

    public MoneyEntry withAmount(BigDecimal value) {
        return new MoneyEntry(value, destination, playerIgn);
    }

    @Override
    public MoneyEntry withDestination(Destination value) {
        return new MoneyEntry(amount, value, playerIgn);
    }

    @Override
    public MoneyEntry withPlayerIgn(String value) {
        return new MoneyEntry(amount, destination, value);
    }
}
