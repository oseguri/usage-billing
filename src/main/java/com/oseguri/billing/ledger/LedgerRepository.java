package com.oseguri.billing.ledger;

import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

@Repository
public class LedgerRepository {

    private static final int CHECK_VIOLATION_ERROR_CODE = 3819;
    private static final String CHECK_ACCOUNT_BALANCE_NON_NEGATIVE = "chk_account_balance_non_negative";

    private final JdbcTemplate jdbcTemplate;

    public LedgerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertLedgerTx(TxType txType, String idempotencyKey) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO ledger_tx (tx_type, idempotency_key) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, txType.name());
            ps.setString(2, idempotencyKey);
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void insertLedgerEntries(long txId, List<LedgerLine> lines) {
        jdbcTemplate.batchUpdate("INSERT INTO ledger_entry (tx_id, account_id, amount_micros) VALUES (?, ?, ?)", lines, lines.size(), (ps, line) -> {
            ps.setLong(1, txId);
            ps.setLong(2, line.accountId());
            ps.setLong(3, line.amountMicros());
        });
    }

    public int updateAccountBalance(LedgerLine ledgerLine) {
        try {
            return jdbcTemplate.update("UPDATE account_balance SET balance_micros = balance_micros + ? WHERE account_id = ?", ledgerLine.amountMicros(), ledgerLine.accountId());
        } catch (UncategorizedSQLException e) {
            if (Objects.requireNonNull(e.getSQLException()).getErrorCode() == CHECK_VIOLATION_ERROR_CODE && e.getMessage().contains(CHECK_ACCOUNT_BALANCE_NON_NEGATIVE)) {
                throw new InsufficientBalanceException(ledgerLine.accountId(), e);
            }
            throw e;
        }
    }
}
