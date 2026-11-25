package com.oseguri.billing.ledger;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public long record(TxType txType, String idempotencyKey, List<LedgerLine> lines) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("잘못된 멱등키 입니다. 멱등 키 : [" + idempotencyKey + "]");
        }
        checkLines(lines);
        long txId;
        try {
            txId = ledgerRepository.insertLedgerTx(txType, idempotencyKey);
        } catch (DuplicateKeyException e) {
            return ledgerRepository.findLedgerTxId(txType, idempotencyKey);
        }
        ledgerRepository.insertLedgerEntries(txId, lines);
        List<LedgerLine> sorted = sortLines(lines);
        for (LedgerLine line : sorted) {
            int updatedNum = ledgerRepository.updateAccountBalance(line);
            if (updatedNum != 1) {
                throw new IllegalStateException(
                        "account_balance에 줄이 없습니다. accountId: " + line.accountId());
            }
        }
        return txId;
    }

    private void checkLines(List<LedgerLine> lines) {
        if (lines.size() < 2) {
            throw new IllegalArgumentException("LedgerLine은 2개 이상이어야 합니다. 현재 개수: " + lines.size());
        }

        Set<Long> seen = new HashSet<>();
        long total = 0;

        for (LedgerLine line : lines) {
            if (!seen.add(line.accountId())) {
                throw new IllegalArgumentException("계정번호가 겹칩니다: " + line.accountId());
            }

            if (line.amountMicros() == 0L) {
                throw new IllegalArgumentException(
                        "Ledger amount가 0 입니다. account_id: " + line.accountId());
            }

            total = Math.addExact(total, line.amountMicros());
        }

        if (total != 0) {
            throw new IllegalArgumentException("합이 0이 아닙니다. 합: " + total);
        }
    }

    private List<LedgerLine> sortLines(List<LedgerLine> lines) {
        return lines.stream().sorted(Comparator.comparingLong(LedgerLine::accountId)).toList();
    }
}
