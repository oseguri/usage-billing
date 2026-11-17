# usage-billing

선불 크레딧으로 유료 API 호출 수만 건을 실행하고, 1원도 어긋나지 않게 과금하는 서버.
부하와 장애를 직접 만들어 넣고, 실행 뒤 검사기로 장부가 맞는지 확인한다.

## 로컬 실행

```
docker compose up -d      # MySQL 8.4, Redis 7
./gradlew :app:bootRun
```

## 문서

- docs/experiments: 실험 기록 (가설 → 조건 → 결과 → 결정)
- docs/notes: 기본기 정리
- docs/decisions.md: 결정과 버린 대안
