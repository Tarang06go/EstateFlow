package com.estateflow.service.biometric;

@FunctionalInterface
public interface CancellationSignal {
    boolean cancelled();

    /** Linearizes cancellation against the start of an irreversible persistence step. */
    default boolean beginCommit() { return !cancelled(); }
}
