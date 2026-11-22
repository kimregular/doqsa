# DOQSA

## Document OCR & Queue Service Architecture

이벤트기반 OCR 풀스택 프로젝트

## 초기 구현 범위

- 이미지 업로드 및 OCR 처리
- OCR 작업 상태 및 결과 조회
- 헥사고날 아키텍처의 큐 포트와 어댑터(롤리팝) 분리
- Spring 비동기 처리(`@Async`) 기반 큐 어댑터 구현

## 큐 확장 방향

- 애플리케이션은 큐 포트에 의존하며, 실행 기술은 어댑터로 분리한다.
- 초기에는 Spring 비동기 어댑터를 사용한다.
- 추후 Redis, Kafka 등의 어댑터(롤리팝)를 추가한다.
- 외부 MQ 도입 시 메시지 발행 정합성과 중복 전달 처리를 함께 구현한다.

## 별도 작업 현황

- [ ] DOQSA 요구사항과 최종 API 계약 정의
- [x] 초기 DOQSA 도메인·엔티티·DTO 구성
- [ ] 사용자 인증과 작업 접근 권한
- [x] 업로드 확인 후 서버 OCR 자동 실행 흐름
- [ ] 추출 대상에 맞는 프롬프트·결과 모델 확장
- [ ] 운영 파일 크기·형식·실제 콘텐츠 검증 (로컬 업로드 크기·요청 메타데이터 검증은 적용)
- [x] Gemini·S3 호출 timeout 및 내부 오류 응답 노출 방지
- [ ] MySQL 설정·스키마 마이그레이션·통합 테스트
- [x] 개발 컨테이너 소스 연결과 Gradle 캐시 설정
- [ ] 전체 서비스 종단 간 테스트 (백엔드 API·큐·복구 통합 테스트는 구현)
- [ ] iOS 촬영·이미지 선택·업로드 기능
- [ ] iOS 작업 상태·목록·결과 화면
- [ ] CI/CD·배포·비밀값 관리
- [ ] 처리 지연·실패율·큐 적체 모니터링

## 구현 상태

영수증 이미지 한 장을 `OcrItem` 하나로 관리하며, 현재 결과는 이름·주소·전화·메모 추출 형식이다.

- `application/port/in`: 작업 생성·업로드 확인·결과 조회·삭제 UseCase
- `application/port/out`: 저장소·이미지 저장소·OCR 분석기·큐·작업 실행 포트
- `adapter/out/queue`: 커밋 후 이벤트와 Spring `@Async` 실행 어댑터
- `adapter/out/storage`: 제공자별 플러그인 패키지(`local`, `s3`). `doqsa.storage.provider` 값으로 하나만 활성화되며, 새 제공자는 하위 패키지와 조건부 `@Configuration` 하나로 추가한다
- `adapter/out/ocr`: 제공자별 OCR 플러그인 패키지(`gemini`, `local`). `doqsa.ocr.provider` 값(`none`·`local`·`gemini`)으로 하나만 활성화되며, 프롬프트와 응답 형식은 `ReceiptExtraction`을 공유한다
- `domain/ocritem`: JPA 엔티티와 상태 전이·재시도·선점 규칙

DB가 대기·실행 상태의 원본이다. 이벤트 누락은 폴링으로 보완하고, 실행 중단은 lease 만료 후 재선점한다.
결과 저장 시 claim token과 만료 시각을 검사한다. 외부 OCR 호출은 중복될 수 있지만 이전 실행자의 결과 저장은 차단한다.
삭제는 논리 삭제이며 저장된 이미지를 물리적으로 삭제하지 않는다.

## 개발 컨테이너 실행 및 테스트

VS Code에서 **Dev Containers: Reopen in Container**를 실행한 뒤 컨테이너 터미널에서 실행한다.

```sh
cd /workspaces/doqsa/backend
./gradlew clean test bootJar
./gradlew bootRun
```

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- REST Docs: `bootJar`로 생성한 JAR 실행 시 `http://localhost:8080/docs/index.html`
- 테스트 보고서: `backend/build/reports/tests/test/index.html`
- 로컬 DB: `backend/data/doqsa.mv.db`
- 로컬 이미지: `backend/data/images/`

기본 설정은 로컬 이미지 저장소와 파일 H2를 사용한다. 인증은 아직 구현하지 않았으므로 로컬 개발 환경에서 사용한다.
기본 OCR 제공자는 비활성화되어 있어 실제 분석 요청은 `OCR_FAILED`로 끝난다. 테스트는 OCR 대역을 사용해 외부 호출 없이 실행한다.
`OCR_PROVIDER=local`로 실행하면 모델 호출 없이 고정 샘플(`resources/ocr/local-receipt.json`, `doqsa.ocr.local.response-location`으로 변경 가능)을 돌려주어 `COMPLETED`까지의 흐름을 확인할 수 있다. 이 플러그인은 `prod` 프로파일에서 비활성화된다.

실제 Gemini OCR은 환경변수를 설정한 후 실행한다. `backend/.env.example`은 참고용이며 자동으로 읽지 않는다.

```sh
export OCR_PROVIDER=gemini
export GOOGLE_GEMINI_API_KEY='<API key>'
export GOOGLE_GEMINI_MODEL=gemini-2.5-flash
./gradlew bootRun
```

S3는 `STORAGE_PROVIDER=s3`, `AWS_S3_BUCKET`, `AWS_REGION`을 설정하고 AWS 기본 자격증명 체인을 사용한다.
버킷 생성·브라우저 CORS·권한 설정은 별도로 필요하다. 로컬 저장소와 업로드 API는 `prod` 프로파일에서 비활성화된다.
운영 DB 설정과 인증은 별도 구현 대상이다.

## 초기 API

| 메서드 | 경로 | 기능 |
|---|---|---|
| POST | `/api/ocr/items` | 이미지 메타데이터 등록과 업로드 URL 발급 |
| POST | `/api/ocr/items/{id}/upload-url` | 업로드 전 URL 재발급 |
| POST | `/api/ocr/items/{id}/upload-complete` | 이미지 존재 확인 후 OCR 큐 등록 |
| GET | `/api/ocr/items/{id}` | 상태·결과·이미지 URL 조회 |
| GET | `/api/ocr/items?cursor={id}&size=20` | 최신순 커서 페이지 조회 |
| DELETE | `/api/ocr/items/{id}` | 작업 논리 삭제 |

생성 요청은 `{"fileName":"receipt.png","contentType":"image/png"}`이며 JPEG·PNG·WebP 메타데이터를 허용한다.
응답의 `uploadUrl`에 이미지 본문을 PUT한 뒤 `upload-complete`를 호출한다.
로컬 URL은 서버 기준 상대 경로이며 유효기간은 10분, 로컬 업로드 크기는 최대 10 MiB이다.

상태 흐름: `WAITING_UPLOAD → UPLOADED → QUEUED → PROCESSING → COMPLETED / FAILED`.
업로드 확인 트랜잭션에서 `UPLOADED`를 거쳐 바로 `QUEUED`로 전환한다.
실패 시 최대 4회 시도하며 재시도 대기 시간은 1·2·4초이다. 동시 실행 수 기본값은 2이다.

## 추후 구현

- PDF 및 다중 페이지 처리
- 여러 문서 유형 지원
- OCR 결과 수정·검색·내보내기
- FCM 기반 OCR 완료 알림 및 푸시 알림

## 구조
```md
doqsa/
  backend/
  ios/
  .devcontainer/
```
