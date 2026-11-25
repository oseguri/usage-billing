package com.oseguri.billing.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.oseguri.billing.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
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

    private long external;
    private long usage;
    private long externalBefore;
    private long usageBefore;

    @BeforeEach
    void setup() {
        external = systemAccountId("EXTERNAL");
        usage = systemAccountId("USAGE");
        externalBefore = balance(external);
        usageBefore = balance(usage);
    }

    @Test
    void 거래를_기록하면_원장과_잔액에_반영된다() {
        long txId = ledgerService.record(TxType.TOPUP, newKey(), externalToUsage(1_000L));

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
        String idempotencyKey = newKey();

        long delta = usageBefore + 1;
        List<LedgerLine> lines =
                List.of(new LedgerLine(external, delta), new LedgerLine(usage, -delta));

        InsufficientBalanceException e =
                assertThrows(
                        InsufficientBalanceException.class,
                        () -> ledgerService.record(TxType.TOPUP, idempotencyKey, lines));

        assertEquals(usage, e.getAccountId());
        assertEquals(0, countTxByKey(idempotencyKey));
        assertEquals(externalBefore, balance(external));
        assertEquals(usageBefore, balance(usage));
    }

    @Test
    void 같은_멱등_키로_두_번_기록하면_기록을_생략하고_반환한다() {

        String idempotencyKey = newKey();

        long txId = ledgerService.record(TxType.TOPUP, idempotencyKey, externalToUsage(1_000L));
        long txId2 = ledgerService.record(TxType.TOPUP, idempotencyKey, externalToUsage(1_000L));

        assertEquals(txId, txId2);
        assertEquals(1, countTxByKey(idempotencyKey));
        assertEquals(externalBefore - 1000, balance(external));
        assertEquals(usageBefore + 1000, balance(usage));
    }

    @Test
    void 두_스레드가_반대_순서로_거래를_업데이트해도_실패하지_않는다() throws Exception {
        int rounds = 50;
        long amount = 1_000L;
        List<LedgerLine> lines = externalToUsage(amount);
        List<LedgerLine> reversed = new ArrayList<>(lines).reversed();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = new ArrayList<>();

        try {
            for (int i = 0; i < rounds; i++) {
                CountDownLatch start = new CountDownLatch(1);

                Future<Void> first =
                        pool.submit(() -> {
                            start.await();
                            ledgerService.record(TxType.TOPUP, newKey(), lines);
                            return null;
                        });

                Future<Void> second =
                        pool.submit(() -> {
                            start.await();
                            ledgerService.record(TxType.TOPUP, newKey(), reversed);
                            return null;
                        });

                start.countDown();

                for (Future<Void> future : List.of(first, second)) {
                    try {
                        future.get(10, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        failures.add(e.getCause());
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }

        long moved = rounds * 2 * amount;
        assertEquals(List.of(), failures);
        assertEquals(externalBefore - moved, balance(external));
        assertEquals(usageBefore + moved, balance(usage));
    }

    /** EXTERNAL에서 USAGE로 amountMicros만큼 옮기는 줄 두 개. */
    private List<LedgerLine> externalToUsage(long amountMicros) {
        return List.of(
                new LedgerLine(external, -amountMicros), new LedgerLine(usage, amountMicros));
    }

    private String newKey() {
        return "test-" + UUID.randomUUID();
    }

    private long countTxByKey(String idempotencyKey) {
        return queryLong(
                "SELECT COUNT(*) FROM ledger_tx WHERE idempotency_key = ?", idempotencyKey);
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
