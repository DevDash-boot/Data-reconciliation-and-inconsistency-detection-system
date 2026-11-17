# 초기 이슈 23개 (복붙용)

사용법
- 이슈는 **번호 순서대로** 생성하세요. (본문의 `#번호` 참조가 맞도록)
- 각 이슈의 `Labels` / `Milestone` / `Project 필드(Week, Size)` 는 제목 아래에 표기했습니다.
- 제목은 `[Type] 내용` 형식 그대로 쓰면 됩니다.
- 현재 마일스톤(M1)만 `Ready`, 나머지는 `Backlog`에 두세요.

---

# M1. 기반 구축 (1~2주)

## #1 [Setup] 레포 초기화
`Labels: type:setup, priority:high` · `Milestone: M1` · `Week 1` · `Size M`

```markdown
## 목적
개발을 시작할 수 있는 최소 환경과 협업 규칙(브랜치/커밋)을 갖춘다.

## 작업 내용
- [ ] Spring Boot 프로젝트 생성 (Java 17, Gradle, Web/JPA/Security/MySQL/Lombok/Mustache)
- [ ] docker-compose.yml 로 MySQL 구성
- [ ] application.yml, .env.example 작성 (비밀값은 .env로 분리)
- [ ] .gitignore 설정 (.env, build, .idea 등)
- [ ] 브랜치 전략 및 커밋 컨벤션을 README에 명시
- [ ] Issue 템플릿(.github/ISSUE_TEMPLATE/task.md) 추가
- [ ] PR 템플릿 추가 (Closes #번호 안내 포함)

## 완료 조건
- [ ] `docker compose up -d` 후 `./gradlew bootRun` 으로 앱이 정상 기동
- [ ] 기본 헬스 확인 URL이 200 응답
- [ ] `.env` 가 커밋되지 않음

## 참고
Wiki: 1. 프로젝트 개요
```

## #2 [Docs] 도메인 및 대조 시나리오 정의
`Labels: type:docs, priority:high` · `Milestone: M1` · `Week 1` · `Size M`

```markdown
## 목적
무엇과 무엇을 어떤 기준으로 대조할지 확정한다. 이후 모든 설계의 기준 문서가 된다.

## 작업 내용
- [ ] 대조 대상 정의: A = 결제 내역(PortOne), B = 주문 DB
- [ ] 각 데이터의 필드 목록 정의 (결제ID, 주문번호, 금액, 상태, 일시 등)
- [ ] 매칭 키 결정 (예: 주문번호 또는 merchant_uid)
- [ ] 불일치 유형 4종 정의 (MISSING_IN_A / MISSING_IN_B / DUPLICATE / VALUE_DIFF)
- [ ] 값 비교 대상 필드와 허용 오차 정책 정의 (예: 금액은 정확 일치)
- [ ] 범위 정의 (하는 것 / 안 하는 것)
- [ ] Wiki 페이지 1, 2 작성

## 완료 조건
- [ ] Wiki 「도메인 정의」에 필드표, 매칭 키, 유형별 판정 규칙이 모두 있다
- [ ] 각 불일치 유형에 예시 데이터가 최소 1개씩 있다

## 참고
Wiki: 2. 도메인 정의
```

## #3 [Design] ERD 설계
`Labels: type:docs, priority:high` · `Milestone: M1` · `Week 2` · `Size M` · 관련: #2

```markdown
## 목적
원천 데이터, 대조 Job, 불일치 결과를 저장할 테이블 구조를 확정한다.

## 작업 내용
- [ ] 원천 테이블 설계: source_payment, source_order
- [ ] 대조 Job 테이블 설계: reconciliation_job (상태, 시작/종료 시각, 요약 건수)
- [ ] 불일치 결과 테이블 설계: mismatch (job_id, type, match_key, 상태, 상세 diff)
- [ ] 업로드 이력 테이블 설계: upload_batch
- [ ] 초기 인덱스 가설 정리 (match_key, job_id+type 등)
- [ ] ERD 이미지를 Wiki에 첨부

## 완료 조건
- [ ] ERD가 Wiki에 있고 각 테이블의 PK/FK/UNIQUE가 명시되어 있다
- [ ] 인덱스를 둔 이유가 한 줄씩 적혀 있다

## 참고
Wiki: 5. ERD & 테이블 설계
```

## #4 [Feature] 샘플 데이터 생성기
`Labels: type:feature, priority:high` · `Milestone: M1` · `Week 2` · `Size L` · 관련: #2, #3

```markdown
## 목적
정답을 알고 있는 테스트 데이터를 만들어 대조 로직의 정확성과 성능을 검증할 수 있게 한다.

## 작업 내용
- [ ] 정상 데이터 생성 (결제/주문 쌍)
- [ ] 비율 조절 옵션: 누락, 중복, 값 불일치 비율
- [ ] 생성 규모 옵션 (1천 / 1만 / 10만 / 100만)
- [ ] 시드(seed) 고정 옵션으로 재현 가능하게 구현
- [ ] "정답 파일(expected)"도 함께 출력 (유형별 기대 건수)
- [ ] CSV 파일로 출력

## 완료 조건
- [ ] 같은 시드로 실행 시 동일한 결과가 나온다
- [ ] 설정한 비율과 expected 파일의 건수가 일치한다
- [ ] 10만 건 생성이 합리적인 시간 안에 끝난다

## 참고
이후 #6, #10, #11 의 검증 기준이 된다.
```

## #5 [Feature] CSV 업로드 및 파싱
`Labels: type:feature, priority:high` · `Milestone: M1` · `Week 2` · `Size L` · 관련: #3, #4

```markdown
## 목적
결제/주문 CSV를 업로드해 원천 테이블에 적재한다.

## 작업 내용
- [ ] 업로드 API (multipart) 구현
- [ ] CSV 파싱 (OpenCSV 등) 및 헤더 검증
- [ ] 행 단위 검증 (필수값, 숫자/날짜 형식)
- [ ] 잘못된 행 처리 정책 결정 (건너뛰고 오류 리포트)
- [ ] upload_batch 에 업로드 이력 저장
- [ ] 우선은 단순 저장 방식(JPA saveAll)으로 구현 (baseline, #12에서 측정)

## 완료 조건
- [ ] 정상 CSV 업로드 시 건수가 원천 테이블에 정확히 저장된다
- [ ] 형식 오류 행이 있어도 전체가 죽지 않고 오류 건수가 응답에 포함된다
- [ ] 업로드 이력이 조회된다

## 참고
성능 개선은 #13에서 진행. 여기서는 정확성 우선.
```

---

# M2. 핵심 대조 로직 (3~4주)

## #6 [Feature] 대조 엔진 v1 (인메모리 baseline)
`Labels: type:feature, priority:high` · `Milestone: M2` · `Week 3` · `Size L` · 관련: #4, #5

```markdown
## 목적
가장 단순한 방식으로 두 데이터셋을 대조하는 기준 구현(baseline)을 만든다.

## 작업 내용
- [ ] A, B 데이터를 매칭 키 기준 Map으로 적재
- [ ] 키 기준 교집합/차집합 계산
- [ ] 키가 같은 쌍의 값 비교
- [ ] 결과를 단순 객체 목록으로 반환 (저장은 #9)
- [ ] 소요 시간/메모리 사용량 로그 출력

## 완료 조건
- [ ] 샘플 데이터(1만 건) 대조 결과가 expected 파일과 일치
- [ ] 1만 / 10만 건 소요 시간을 Wiki 성능 기록에 남김

## 참고
이 구현은 의도적으로 단순하게 유지한다. 개선 과정을 Wiki「대조 알고리즘」에 기록할 것.
```

## #7 [Feature] MatchingStrategy 인터페이스 도입
`Labels: type:feature, priority:medium` · `Milestone: M2` · `Week 3` · `Size M` · 관련: #6

```markdown
## 목적
비교 규칙을 교체 가능하게 분리해 확장성을 확보한다. (Strategy 패턴)

## 작업 내용
- [ ] MatchingStrategy 인터페이스 정의 (예: boolean isMatch / List<FieldDiff> compare)
- [ ] ExactMatchStrategy 구현 (모든 필드 정확 일치)
- [ ] AmountOnlyStrategy 구현 (금액만 비교) 등 최소 2개 구현
- [ ] 대조 엔진이 Strategy를 주입받도록 리팩터링
- [ ] 설정(job 파라미터)으로 Strategy 선택
- [ ] ADR 작성: "왜 Strategy 패턴을 썼는가"

## 완료 조건
- [ ] Strategy를 바꿔도 엔진 코드를 수정하지 않는다
- [ ] 전략별 단위 테스트 통과
- [ ] Wiki ADR 1건 추가

## 참고
Wiki: 8. 의사결정 기록
```

## #8 [Feature] 불일치 유형 분류
`Labels: type:feature, priority:high` · `Milestone: M2` · `Week 3` · `Size M` · 관련: #6, #7

```markdown
## 목적
비교 결과를 유형별로 분류해 이후 처리 상태 관리의 기준으로 사용한다.

## 작업 내용
- [ ] MismatchType enum 정의 (MISSING_IN_A, MISSING_IN_B, DUPLICATE, VALUE_DIFF)
- [ ] 유형별 판정 로직 구현
- [ ] DUPLICATE: 한쪽에 같은 키가 2건 이상인 경우 처리
- [ ] VALUE_DIFF: 어떤 필드가 어떻게 다른지 (필드명, A값, B값) 함께 저장
- [ ] 한 키가 여러 유형에 해당할 때의 우선순위 규칙 정의 및 문서화

## 완료 조건
- [ ] 샘플 데이터 기준 유형별 건수가 생성기 expected 값과 일치
- [ ] 유형별 단위 테스트 통과
- [ ] 우선순위 규칙이 Wiki에 기록됨

## 참고
Wiki: 2. 도메인 정의 (불일치 유형)
```

## #9 [Feature] 대조 Job 실행 API 및 결과 저장
`Labels: type:feature, priority:high` · `Milestone: M2` · `Week 4` · `Size L` · 관련: #3, #8

```markdown
## 목적
대조를 Job 단위로 실행하고, 이력과 결과를 DB에 남긴다.

## 작업 내용
- [ ] POST /api/jobs : 대조 Job 생성 및 실행
- [ ] Job 상태 관리 (PENDING / RUNNING / SUCCESS / FAILED)
- [ ] 불일치 결과를 mismatch 테이블에 저장
- [ ] Job 요약 저장 (총 건수, 유형별 건수, 소요 시간)
- [ ] GET /api/jobs/{id} : Job 조회
- [ ] 실패 시 상태/오류 메시지 기록, 트랜잭션 처리

## 완료 조건
- [ ] Job 실행 후 결과가 DB에 저장되고 요약 조회가 된다
- [ ] 실행 중 예외 시 Job이 FAILED로 남고 부분 저장이 남지 않는다

## 참고
멱등성은 #16에서 강화한다.
```

## #10 [Test] 대조 로직 단위 테스트
`Labels: type:test, priority:high` · `Milestone: M2` · `Week 4` · `Size M` · 관련: #6, #7, #8

```markdown
## 목적
핵심 로직을 테스트로 보호해 이후 성능 개선 시 회귀를 막는다.

## 작업 내용
- [ ] 유형별 케이스 테스트 (정상, 누락A, 누락B, 중복, 값불일치)
- [ ] 경계 케이스 (빈 데이터, 한쪽만 비어 있음, 전부 불일치, null 필드)
- [ ] Strategy별 테스트
- [ ] given / when / then 패턴 유지

## 완료 조건
- [ ] 위 케이스 모두 통과
- [ ] 대조 엔진 패키지 라인 커버리지 확인 및 Wiki 기록

## 참고
성능 개선(#13~#15) 전후로 이 테스트가 통과해야 한다.
```

---

# M3. 성능·안정성 (5~6주)

## #11 [Perf] 대용량 데이터셋 생성
`Labels: type:perf, priority:high` · `Milestone: M3` · `Week 5` · `Size S` · 관련: #4

```markdown
## 목적
성능 측정에 쓸 표준 데이터셋을 준비한다.

## 작업 내용
- [ ] 1만 / 10만 / 100만 건 데이터셋 생성 (시드 고정)
- [ ] 각 데이터셋의 expected 건수 파일 보관
- [ ] 저장 위치/생성 명령을 README 또는 Wiki에 기록 (대용량 파일은 Git에 커밋하지 않음)

## 완료 조건
- [ ] 세 규모의 데이터셋을 명령어 하나로 재생성 가능
```

## #12 [Perf] baseline 성능 측정 및 기록
`Labels: type:perf, priority:high` · `Milestone: M3` · `Week 5` · `Size M` · 관련: #11

```markdown
## 목적
개선 전 수치를 남겨 이후 개선 효과를 증명한다.

## 작업 내용
- [ ] 측정 환경 기록 (CPU, RAM, MySQL 버전, 설정)
- [ ] 단계별 측정: 업로드 적재 / 대조 / 결과 저장
- [ ] 각 규모에서 3회 측정 후 평균 기록
- [ ] 병목 지점 분석 (쿼리 로그, 프로파일링)
- [ ] Wiki「성능 측정 기록」에 표로 정리

## 완료 조건
- [ ] 1만 / 10만 / 100만 건에서 단계별 시간이 기록됨
- [ ] 가장 느린 구간 1~2곳이 명시됨
```

## #13 [Perf] JDBC batch insert 적용
`Labels: type:perf, priority:high` · `Milestone: M3` · `Week 5` · `Size L` · 관련: #12

```markdown
## 목적
대량 적재 구간의 병목을 JDBC batch로 개선한다.

## 작업 내용
- [ ] JdbcTemplate(or JDBC) batchUpdate 로 적재/결과 저장 구현
- [ ] rewriteBatchedStatements 등 드라이버 옵션 검토
- [ ] batch size별 측정 (500 / 1000 / 5000 등)
- [ ] JPA saveAll 대비 개선율 기록
- [ ] ADR: "JPA 대신 JDBC batch를 선택한 이유"

## 완료 조건
- [ ] 동일 데이터셋에서 baseline 대비 개선된 수치가 Wiki에 기록됨
- [ ] 기존 단위 테스트(#10) 통과
```

## #14 [Perf] 청크 단위 처리 적용
`Labels: type:perf, priority:medium` · `Milestone: M3` · `Week 6` · `Size L` · 관련: #12, #13

```markdown
## 목적
전체를 메모리에 올리는 방식의 한계를 개선해 100만 건 이상에서도 안정적으로 동작하게 한다.

## 작업 내용
- [ ] 100만 건에서 baseline의 메모리 사용량/OOM 여부 확인
- [ ] 키 범위 또는 해시 파티션 기반 청크 분할 대조 구현
- [ ] 청크 크기별 시간/메모리 측정
- [ ] 청크 경계에서 결과가 누락/중복되지 않는지 검증

## 완료 조건
- [ ] 100만 건에서 제한된 힙으로도 정상 완료
- [ ] 청크 방식 결과가 인메모리 방식 결과와 동일 (expected 일치)
```

## #15 [Perf] 인덱스 설계 및 EXPLAIN 분석
`Labels: type:perf, priority:medium` · `Milestone: M3` · `Week 6` · `Size M` · 관련: #3, #12

```markdown
## 목적
조회/대조 쿼리를 인덱스로 최적화하고 근거를 남긴다.

## 작업 내용
- [ ] 주요 쿼리 목록 정리 (키 조회, 불일치 목록 필터, 상태별 조회)
- [ ] 인덱스 적용 전 EXPLAIN 결과 기록
- [ ] 복합 인덱스 설계 및 적용 (컬럼 순서 근거 포함)
- [ ] 적용 후 EXPLAIN 및 소요 시간 비교
- [ ] 쓰기 성능에 미치는 영향도 함께 측정

## 완료 조건
- [ ] 적용 전/후 EXPLAIN과 시간이 Wiki에 기록됨
- [ ] 인덱스별 존재 이유가 문서화됨
```

## #16 [Feature] 멱등성 보장
`Labels: type:feature, priority:high` · `Milestone: M3` · `Week 6` · `Size L` · 관련: #9

```markdown
## 목적
같은 대조를 여러 번 실행하거나 중간에 실패 후 재실행해도 결과가 꼬이지 않게 한다.

## 작업 내용
- [ ] "동일 Job"의 정의 (입력 배치 + 전략 + 기간 등) 결정
- [ ] 동일 Job 재실행 시 기존 결과 처리 정책 결정 (교체 or 거부)
- [ ] mismatch UNIQUE 제약 (job_id, match_key, type) 적용
- [ ] 실패 후 재실행 시나리오 처리 (부분 저장 정리)
- [ ] 동시 실행 방지 (동일 Job 중복 실행 차단)
- [ ] ADR 작성

## 완료 조건
- [ ] 같은 요청을 3회 보내도 mismatch 건수가 동일
- [ ] 실패 후 재실행 테스트 통과
- [ ] 동시 요청 시 한 건만 실행됨 (테스트로 검증)
```

## #17 [Feature] @Scheduled 주기 대조
`Labels: type:feature, priority:low` · `Milestone: M3` · `Week 6` · `Size M` · 관련: #9, #16

```markdown
## 목적
정해진 주기로 대조를 자동 실행한다.

## 작업 내용
- [ ] 스케줄러 구현 (cron 설정은 application.yml로 분리)
- [ ] 대상 기간 계산 (예: 전일 데이터)
- [ ] 스케줄 실행 이력/실패 로그
- [ ] 중복 실행 방지 (#16 재사용)

## 완료 조건
- [ ] 설정한 주기에 Job이 자동 생성/실행됨
- [ ] 스케줄러를 끌 수 있는 설정 플래그가 있다
```

---

# M4. 운영 기능·마무리 (7~8주)

## #18 [Feature] 불일치 조회/필터 API 및 화면
`Labels: type:feature, priority:high` · `Milestone: M4` · `Week 7` · `Size L` · 관련: #9, #15

```markdown
## 목적
운영자가 불일치를 찾아보고 상세를 확인할 수 있게 한다.

## 작업 내용
- [ ] GET /api/mismatches : 유형/상태/Job/기간 필터, 페이징
- [ ] 상세 조회 (VALUE_DIFF의 필드별 A/B 값 표시)
- [ ] Mustache + Bootstrap 목록/상세 화면
- [ ] 대량 결과에서도 느려지지 않는지 확인 (인덱스 활용)

## 완료 조건
- [ ] 필터 조합으로 조회 가능, 페이징 동작
- [ ] 10만 건 이상 결과에서 목록 응답이 합리적인 시간 이내
```

## #19 [Feature] 처리 상태 관리
`Labels: type:feature, priority:high` · `Milestone: M4` · `Week 7` · `Size M` · 관련: #18

```markdown
## 목적
불일치가 "확인되고 해결됐는지"를 추적한다.

## 작업 내용
- [ ] 상태 정의: OPEN → RESOLVED / IGNORED
- [ ] 허용되는 상태 전이 규칙 정의 (예: RESOLVED → OPEN 재오픈 여부)
- [ ] PATCH /api/mismatches/{id}/status
- [ ] 처리자, 처리 시각, 메모 기록
- [ ] 다건 일괄 상태 변경 (선택)

## 완료 조건
- [ ] 허용되지 않은 전이는 400 응답
- [ ] 상태 변경 이력이 남는다
- [ ] 상태 전이 단위 테스트 통과
```

## #20 [Feature] 대시보드 (Chart.js)
`Labels: type:feature, priority:medium` · `Milestone: M4` · `Week 7` · `Size M` · 관련: #18, #19

```markdown
## 목적
불일치 현황을 한눈에 확인한다.

## 작업 내용
- [ ] 통계 API: 유형별 건수, 상태별 건수, 일별 추이
- [ ] Chart.js 차트 3종 (유형 도넛, 상태 막대, 추이 라인)
- [ ] 최근 Job 요약 카드

## 완료 조건
- [ ] 대시보드 수치가 DB 직접 조회 결과와 일치
- [ ] 통계 쿼리가 인덱스를 사용 (EXPLAIN 확인)
```

## #21 [Feature] ADMIN 권한 적용
`Labels: type:feature, priority:medium` · `Milestone: M4` · `Week 8` · `Size M`

```markdown
## 목적
민감한 기능(Job 실행, 상태 변경)을 권한으로 제한한다.

## 작업 내용
- [ ] 로그인/회원가입 (Spring Security)
- [ ] ADMIN / USER 역할 정의
- [ ] 권한 정책 표 작성 (예: USER=조회만, ADMIN=Job 실행/상태 변경/업로드)
- [ ] 미인증/권한 부족 시 응답 처리 (401 / 403)

## 완료 조건
- [ ] 권한 정책 표대로 접근이 제어됨
- [ ] 권한별 접근 테스트 통과
```

## #22 [Test] 통합 테스트
`Labels: type:test, priority:high` · `Milestone: M4` · `Week 8` · `Size L`

```markdown
## 목적
업로드 → 대조 → 조회 → 상태 변경의 전체 흐름이 동작함을 검증한다.

## 작업 내용
- [ ] Testcontainers(MySQL) 또는 테스트용 DB 구성
- [ ] 시나리오 테스트: CSV 업로드 → Job 실행 → 유형별 건수 검증
- [ ] 재실행 멱등성 시나리오
- [ ] 권한 시나리오

## 완료 조건
- [ ] `./gradlew test` 한 번으로 전체 통과
- [ ] 테스트 방법이 README에 기재됨
```

## #23 [Docs] README, 성능 결과 정리, 회고
`Labels: type:docs, priority:high` · `Milestone: M4` · `Week 8` · `Size M`

```markdown
## 목적
프로젝트를 처음 보는 사람이 5분 안에 이해하도록 마무리한다.

## 작업 내용
- [ ] README 최종화 (실행 방법이 실제로 동작하는지 새 환경에서 확인)
- [ ] 성능 결과 요약표 README에 반영 (Before/After)
- [ ] 아키텍처/ERD 최종본 반영
- [ ] 회고 작성 (가장 어려웠던 문제 3가지, 선택과 이유, 아쉬운 점)
- [ ] 다음 단계 아이디어 정리 (퍼지 매칭, Excel 지원 등)

## 완료 조건
- [ ] 클론 후 README만 보고 실행 가능
- [ ] 성능 수치와 근거(Wiki 링크)가 README에 있다
```
