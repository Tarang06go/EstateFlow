package com.estateflow.dto;

import com.estateflow.model.DepositTransaction;

/** A ledger entry with the names the report needs. */
public record DepositActivityRow(DepositTransaction txn, long agreementId, String propertyName, String tenantName) {
}
