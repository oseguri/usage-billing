package com.oseguri.billing.ledger;

import java.util.List;
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
        checkLines(lines);
        long txId = ledgerRepository.insertLedgerTx(txType, idempotencyKey);
        ledgerRepository.insertLedgerEntries(txId, lines);
        for (LedgerLine line : lines) {
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

        long total = 0;

        for (LedgerLine line : lines) {
            total = Math.addExact(total, line.amountMicros());
            if (line.amountMicros() == 0L) {
                throw new IllegalArgumentException(
                        "Ledger amount가 0 입니다. account_id: " + line.accountId());
            }
        }

        if (total != 0) {
            throw new IllegalArgumentException("합이 0이 아닙니다. 합: " + total);
        }
    }
}
