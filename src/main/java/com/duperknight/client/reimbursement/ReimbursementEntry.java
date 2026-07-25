package com.duperknight.client.reimbursement;

/** One immutable item or money request in a reimbursement draft. */
public sealed interface ReimbursementEntry permits ItemEntry, MoneyEntry {
    Destination destination();

    String playerIgn();

    ReimbursementEntry withDestination(Destination destination);

    ReimbursementEntry withPlayerIgn(String playerIgn);
}
