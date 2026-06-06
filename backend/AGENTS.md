# AGENTS.md

## 프로젝트 개요

**DOQSA** — Document OCR & Queue Service Architecture
이벤트 기반 OCR 풀스택 프로젝트

---

## 아키텍처

헥사고날 아키텍처를 기반으로 하되, **도메인은 JPA에 종속적**으로 설계한다.
순수 도메인 모델 분리는 적용하지 않는다.

### 레이어 구조

```
adapter/in           → HTTP 요청 수신, UseCase 호출
application/port/in  → UseCase 인터페이스
application/port/out → 저장소 인터페이스
adapter/out          → Port 구현, JPA 사용
domain               → JPA 엔티티 (도메인 모델)
```

---

## 파일 구조

```
doqsa/
├── .devcontainer/
│   ├── devcontainer.json
│   ├── Dockerfile
│   └── docker-compose.yml
├── backend/
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew
│   ├── gradle/
│   │   └── wrapper/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/doqsa/backend/
│   │   │   │       ├── domain/
│   │   │   │       │   └── ocritem/
│   │   │   │       │       ├── OcrItem.java
│   │   │   │       │       └── OcrStatus.java
│   │   │   │       ├── application/
│   │   │   │       │   └── port/
│   │   │   │       │       ├── in/
│   │   │   │       │       └── out/
│   │   │   │       └── adapter/
│   │   │   │           ├── in/
│   │   │   │           │   └── web/
│   │   │   │           └── out/
│   │   │   │               └── persistence/
│   │   │   └── resources/
│   │   └── test/
│   └── TODO.md
└── ios/
```

---

## TODO.md 작업 상태 표기

`backend/TODO.md` 파일에 작업 목록을 관리한다.

| 상태 | 의미 |
|------|------|
| `* [ ]` | 시작 전 |
| `* [🧑‍💻]` | 작업 중 |
| `* [✅]` | 작업 완료 |
| `* [😵]` | 리뷰 필요 / 컴파일 불가 / 테스트 실패 |

### 예시

```markdown
* [✅] OcrItem 도메인 엔티티 작성
* [🧑‍💻] UploadOcrItemUseCase 구현
* [ ] OcrItemController 작성
* [😵] OcrItemPersistenceAdapter 테스트 실패
```

---

## 개발 환경

- **Java** 25
- **Spring Boot** 4.0.5
- **Build Tool** Gradle
- **DB (local)** H2
- **DB (prod)** MySQL
- **MQ** 추후 도입 예정
- **Dev Container** mcr.microsoft.com/devcontainers/java:25