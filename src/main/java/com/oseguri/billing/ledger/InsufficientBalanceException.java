package com.oseguri.billing.ledger;

public class InsufficientBalanceException extends RuntimeException {

    private final long accountId;

    public InsufficientBalanceException(long accountId, Throwable cause) {
        super("잔고가 부족합니다 account_id: " + accountId, cause);
        this.accountId = accountId;
    }

    public long getAccountId() {
        return accountId;
    }
}
