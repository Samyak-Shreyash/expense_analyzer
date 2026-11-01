package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.transaction.model.Transaction;
import java.time.Instant;

/** Pairs money moved between a user's own accounts. Only the user pairs rows; nothing is ever paired automatically. */
final class TransferService {

    private TransferService() {
    }

    /** Links two transactions as the halves of one transfer. */
    static void pair(Transaction a, Transaction b, boolean manual, Instant now) {
        for (Transaction[] t : new Transaction[][] {{a, b}, {b, a}}) {
            t[0].setTransfer(true);
            t[0].setTransferPairId(t[1].getId());
            t[0].setTransferManual(manual);
            t[0].setUpdatedAt(now);
        }
    }
}
