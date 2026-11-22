# Repository Guidelines

## 프로젝트 구조

DOQSA는 이벤트 기반 문서 OCR 서비스입니다. Java 25와 Spring Boot를 사용하는 백엔드는 `backend/`에 있으며, 소스 코드는 `backend/src/main/java/com/doqsa/backend/`에 있습니다. HTTP 어댑터는 `adapter/in/web/`에, 유스케이스와 처리 흐름은 `application/`에 둡니다. 영속성, 이미지 저장소, OCR, 큐에 대한 인터페이스는 `application/port/out/`에 정의하고 구현은 `adapter/out/`에 둡니다. OCR 상태 전이 규칙은 `domain/ocritem/`의 JPA 도메인 모델에서 관리합니다.

설정과 OCR 프롬프트는 `backend/src/main/resources/`에, 테스트와 테스트 데이터는 `backend/src/test/`에, API 문서는 `backend/src/docs/asciidoc/`에 있습니다. 백엔드 세부 지침은 `backend/AGENTS.md`를 참고하세요.

## 개발 컨테이너에서 빌드, 테스트, 실행

VS Code에서 **Dev Containers: Reopen in Container**를 실행해 `.devcontainer/`에 정의된 개발 컨테이너를 여세요. 모든 빌드, 테스트, 애플리케이션 실행은 반드시 개발 컨테이너 안에서 진행하세요. 다음 명령은 컨테이너 터미널의 `/workspaces/doqsa/backend/`에서 실행합니다.

- `./gradlew bootRun`은 포트 8080에서 API를 실행합니다. 기본 설정은 파일 기반 H2와 로컬 이미지 저장소를 사용합니다.
- `./gradlew test`는 전체 테스트를 실행합니다. HTML 보고서는 `build/reports/tests/test/index.html`에서 확인할 수 있습니다.
- `./gradlew clean test bootJar`는 이전 빌드 결과를 지우고 테스트를 실행한 뒤, 생성된 REST Docs를 포함한 실행 가능한 JAR를 만듭니다.

## 코드 스타일과 이름 규칙

수정하는 파일의 들여쓰기 방식을 따르세요. 최근 백엔드 Java 코드는 공백 4칸을 사용하며, 기본 애플리케이션 클래스와 Gradle 스크립트는 탭을 사용합니다. 패키지 이름은 소문자, Java 타입은 PascalCase, 메서드와 필드는 camelCase로 작성하세요. `OcrUseCase`와 `OcrItemPersistenceAdapter`처럼 역할이 드러나는 이름을 사용하세요.

## 테스트 지침

단위 테스트에는 JUnit Jupiter를, API 통합 테스트에는 Spring Boot와 MockMvc를 사용하세요. 테스트 클래스 이름은 `OcrItemTest`, `OcrIntegrationTest` 같은 기존 패턴을 따릅니다. 특정 테스트 클래스는 `./gradlew test --tests '*OcrIntegrationTest'`로 실행할 수 있습니다. 외부 OCR 및 저장소 호출에는 테스트 대역을 사용하고, API 계약이 바뀌면 REST Docs 스니펫을 갱신하세요. Gradle 빌드에 설정된 최소 커버리지 기준은 없습니다.

## 커밋과 풀 리퀘스트

최근 커밋처럼 `docs: modify gitignore`, `refactor: merge multiple .gitignore files into one` 등 변경 유형으로 시작하는 짧은 제목을 사용하세요. 풀 리퀘스트에는 변경 내용과 검증 결과를 적고 관련 이슈가 있으면 링크를 추가하세요. 환경 변수는 `backend/.env.example`을 참고하고, 실제 자격 증명은 커밋에 포함하지 마세요.
