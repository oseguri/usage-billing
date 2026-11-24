package com.oseguri.billing.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.oseguri.billing.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public class LedgerServiceIntegrationTest {

    @Autowired LedgerService ledgerService;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 거래를_기록하면_원장과_잔액에_반영된다() {
        // 1. EXTERNAL, USAGE 계정 번호를 조회한다
        long external = systemAccountId("EXTERNAL");
        long usage = systemAccountId("USAGE");

        // 2. 기록 전 두 계정의 잔액을 읽어 둔다
        long externalBefore = balance(external);
        long usageBefore = balance(usage);

        String idempotencyKey = "test-" + UUID.randomUUID();
        // 3. EXTERNAL -1000, USAGE +1000 거래를 기록한다
        long txId =
                ledgerService.record(
                        TxType.TOPUP,
                        idempotencyKey,
                        List.of(new LedgerLine(external, -1000L), new LedgerLine(usage, 1000L)));

        // 4. 확인한다
        assertEquals(1, queryLong("SELECT COUNT(*) FROM ledger_tx WHERE id = ?", txId));
        assertEquals(2, queryLong("SELECT COUNT(*) FROM ledger_entry WHERE tx_id = ?", txId));
        assertEquals(
                0, queryLong("SELECT SUM(amount_micros) FROM ledger_entry WHERE tx_id = ?", txId));
        assertEquals(externalBefore - 1000, balance(external));
        assertEquals(usageBefore + 1000, balance(usage));
    }

    @Test
    void 음수가_허용되지_않는_계정이_음수가_되면_ROLLBACK() {
        // 1. EXTERNAL, USAGE 계정 번호를 조회한다
        long external = systemAccountId("EXTERNAL");
        long usage = systemAccountId("USAGE");

        // 2. 기록 전 두 계정의 잔액을 읽어 둔다
        long externalBefore = balance(external);
        long usageBefore = balance(usage);

        String idempotencyKey = "test-" + UUID.randomUUID();
        long delta = usageBefore + 1;
        // 3. EXTERNAL delta, USAGE -delta 거래를 기록한다
        InsufficientBalanceException e =
                assertThrows(
                        InsufficientBalanceException.class,
                        () ->
                                ledgerService.record(
                                        TxType.TOPUP,
                                        idempotencyKey,
                                        List.of(
                                                new LedgerLine(external, delta),
                                                new LedgerLine(usage, -delta))));

        // 4. 확인한다
        assertEquals(usage, e.getAccountId());
        assertEquals(
                0,
                queryLong(
                        "SELECT COUNT(*) FROM ledger_tx WHERE idempotency_key = ?",
                        idempotencyKey));
        assertEquals(externalBefore, balance(external));
        assertEquals(usageBefore, balance(usage));
    }

    @Test
    void 같은_멱등_키로_두_번_기록하면_기록을_생략하고_반환한다() {
        // 1. EXTERNAL, USAGE 계정 번호를 조회한다
        long external = systemAccountId("EXTERNAL");
        long usage = systemAccountId("USAGE");

        // 2. 기록 전 두 계정의 잔액을 읽어 둔다
        long externalBefore = balance(external);
        long usageBefore = balance(usage);

        String idempotencyKey = "test-" + UUID.randomUUID();
        // 3. EXTERNAL -1000, USAGE +1000 거래를 기록한다
        long txId =
                ledgerService.record(
                        TxType.TOPUP,
                        idempotencyKey,
                        List.of(new LedgerLine(external, -1000L), new LedgerLine(usage, 1000L)));

        long txId2 =
                ledgerService.record(
                        TxType.TOPUP,
                        idempotencyKey,
                        List.of(new LedgerLine(external, -1000L), new LedgerLine(usage, 1000L)));

        // 4. 확인한다
        assertEquals(
                1,
                queryLong(
                        "SELECT COUNT(*) FROM ledger_tx WHERE idempotency_key = ?",
                        idempotencyKey));
        assertEquals(txId, txId2);
        assertEquals(externalBefore - 1000, balance(external));
        assertEquals(usageBefore + 1000, balance(usage));
    }

    private long balance(long accountId) {
        return queryLong(
                "SELECT balance_micros FROM account_balance WHERE account_id = ?", accountId);
    }

    private long systemAccountId(String accountType) {
        return queryLong(
                "SELECT id FROM account WHERE owner_id = 'SYSTEM' AND account_type = ?",
                accountType);
    }

    private long queryLong(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
