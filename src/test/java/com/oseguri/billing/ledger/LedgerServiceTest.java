package com.oseguri.billing.ledger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LedgerServiceTest {

    LedgerService service = new LedgerService(null);

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLines")
    void 잘못된_줄_목록은_거부한다(String caseName, List<LedgerLine> lines, String expectedMessage) {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(TxType.TOPUP, "key-1", lines));
        assertTrue(e.getMessage().contains(expectedMessage));
    }

    @ParameterizedTest(name = "[{index}] 멱등키가 \"{0}\"일 때 IllegalArgumentException 발생")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "\t", "\n"})
    void 키가_null_이거나_비어_있으면_예외가_발생한다(String idempotencyKey) {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.record(TxType.TOPUP, idempotencyKey, null));
        assertTrue(e.getMessage().contains("잘못된 멱등키 입니다."));
    }

    static Stream<Arguments> invalidLines() {
        return Stream.of(
                Arguments.of(
                        "합이 0이 아님",
                        List.of(new LedgerLine(1L, -1000L), new LedgerLine(2L, 900L)),
                        "합이 0이 아닙니다. 합: "),
                Arguments.of(
                        "금액이 0인 줄이 있음",
                        List.of(
                                new LedgerLine(1L, 1000L),
                                new LedgerLine(2L, -1000L),
                                new LedgerLine(3L, 0L)),
                        "Ledger amount가 0 입니다. account_id: "),
                Arguments.of("줄이 2개 미만", List.of(new LedgerLine(1L, -1000L)), "2개 이상"),
                // 합이 0이고 0인 줄도 없어서 다른 검사에는 걸리지 않는다
                Arguments.of(
                        "같은 계정이 두 줄에 나옴",
                        List.of(
                                new LedgerLine(1L, -1000L),
                                new LedgerLine(2L, 600L),
                                new LedgerLine(2L, 400L)),
                        "계정번호가 겹칩니다: 2"));
    }
}
