package com.oseguri.billing.ledger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerServiceTest {

    LedgerService service = new LedgerService(null);

    @Test
    void 합이_0이_아니면_거부한다() {
        List<LedgerLine> lines = List.of(new LedgerLine(1L, -1000L), new LedgerLine(2L, 900L));

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(TxType.TOPUP, "key-1", lines));

        assertTrue(e.getMessage().contains("합이 0이 아닙니다. 합: "));
    }

    @Test
    void 값이_하나라도_0이면_거부한다() {
        List<LedgerLine> lines =
                List.of(
                        new LedgerLine(1L, 1000L),
                        new LedgerLine(2L, -1000L),
                        new LedgerLine(3L, 0L));

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(TxType.TOPUP, "key-1", lines));

        assertTrue(e.getMessage().contains("Ledger amount가 0 입니다. account_id: "));
    }

    @Test
    void 줄이_2개_미만이면_거부한다() {
        List<LedgerLine> lines = List.of(new LedgerLine(1L, -1000L));

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(TxType.TOPUP, "key-1", lines));

        assertTrue(e.getMessage().contains("2개 이상"));
    }

    @Test
    void 같은_계정이_두_줄에_나오면_거부한다() {
        // 합이 0이고 0인 줄도 없어서 다른 검사에는 걸리지 않는다
        List<LedgerLine> lines =
                List.of(
                        new LedgerLine(1L, -1000L),
                        new LedgerLine(2L, 600L),
                        new LedgerLine(2L, 400L));

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(TxType.TOPUP, "key-1", lines));

        assertTrue(e.getMessage().contains("계정번호가 겹칩니다: 2"));
    }
}
