CREATE TABLE account
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    owner_id     VARCHAR(50) NOT NULL,
    account_type ENUM('WALLET', 'RESERVED', 'USAGE', 'PLATFORM_LOSS', 'EXTERNAL') NOT NULL,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_owner_type (owner_id, account_type)
) ENGINE=InnoDB;

CREATE TABLE account_balance
(
    account_id     BIGINT  NOT NULL,
    balance_micros BIGINT  NOT NULL DEFAULT 0,
    allow_negative BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (account_id),
    CONSTRAINT fk_account_balance_account
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT chk_account_balance_non_negative
        CHECK (allow_negative OR balance_micros >= 0)
) ENGINE=InnoDB;

CREATE TABLE ledger_tx
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tx_type         ENUM('TOPUP', 'RESERVE', 'SETTLE', 'RELEASE', 'ADJUST') NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_tx_type_key (tx_type, idempotency_key)
) ENGINE=InnoDB;


CREATE TABLE ledger_entry
(
    id            BIGINT NOT NULL AUTO_INCREMENT,
    tx_id         BIGINT NOT NULL,
    account_id    BIGINT NOT NULL,
    amount_micros BIGINT NOT NULL,

    PRIMARY KEY (id),
    KEY           idx_ledger_entry_account (account_id, id),
    CONSTRAINT fk_ledger_entry_tx
        FOREIGN KEY (tx_id) REFERENCES ledger_tx (id),
    CONSTRAINT fk_ledger_entry_account
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT chk_ledger_entry_amount_nonzero
        CHECK (amount_micros <> 0)
) ENGINE=InnoDB;