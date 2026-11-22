**OpenAI·Grok OCR 플러그인.** 두 구현은 `adapter/out/ocr/openai`, `adapter/out/ocr/grok` 플러그인 패키지에서 `OcrImageAnalyzer` 포트를 구현한다. 플러그인 규약은 `adapter/out/ocr/package-info.java`를 따른다. 영수증 프롬프트와 이미지 바이트를 Chat Completions API에 보내고 `detected_info`를 `Receipt(name, address, tell, mobile, memo)`로 변환한다. 모델의 네이티브 JSON Schema 기능을 강제하지 않고 공통 `ReceiptExtraction` 프롬프트와 Spring AI의 타입 변환을 사용한다.

| 선택 | 조건 | 기본 모델 | 기본 base URL | API 키 |
|---|---|---|---|---|
| 비활성 | `doqsa.ocr.provider=none` | 없음 | 없음 | 불필요 |
| OpenAI | `doqsa.ocr.provider=openai` | `gpt-4o-mini` | `https://api.openai.com/v1` | `OPENAI_API_KEY` |
| Grok | `doqsa.ocr.provider=grok` | `grok-2-vision-1212` | `https://api.x.ai/v1` | `XAI_API_KEY` |
| Gemini | `doqsa.ocr.provider=gemini` | `gemini-2.5-flash` | SDK 기본값 | `GOOGLE_GEMINI_API_KEY` |

`OCR_PROVIDER` 환경변수는 `doqsa.ocr.provider`로 연결된다. Spring AI chat 자동설정은 꺼져 있고(`spring.ai.model.chat=none`) 각 플러그인이 자기 모델을 직접 만든다. 새 OpenAI 스타터의 임베딩·이미지 생성·음성·moderation 모델은 비활성화하므로, Grok·Gemini·none 모드에 OpenAI 키를 추가로 설정할 필요가 없다.

선택된 공급자의 프로퍼티만 등록·검증한다. 선택되지 않은 공급자는 API 키가 비어 있어도 기동에 영향을 주지 않는다. 각 구성은 별도 이름의 HTTP client, OpenAiApi, OpenAiChatModel, 분석기 빈을 등록한다. API 빈을 명시해 OpenAI 자동설정이 기본 키로 별도 클라이언트를 만들지 않도록 한다. HTTP client는 컨텍스트 종료 시 닫힌다.

| 설정 | 환경변수 | 기본값 |
|---|---|---|
| `doqsa.ocr.openai.api-key` | `OPENAI_API_KEY` | 선택 시 필수 |
| `doqsa.ocr.openai.model` | `OPENAI_OCR_MODEL` | gpt-4o-mini |
| `doqsa.ocr.openai.base-url` | `OPENAI_BASE_URL` | https://api.openai.com/v1 |
| `doqsa.ocr.openai.timeout` | Spring 표준 환경변수 바인딩 지원 | doqsa.queue.external-call-timeout, 기본 30s |
| `doqsa.ocr.grok.api-key` | `XAI_API_KEY` | 선택 시 필수 |
| `doqsa.ocr.grok.model` | `GROK_OCR_MODEL` | grok-2-vision-1212 |
| `doqsa.ocr.grok.base-url` | `XAI_BASE_URL` | https://api.x.ai/v1 |
| `doqsa.ocr.grok.timeout` | Spring 표준 환경변수 바인딩 지원 | doqsa.queue.external-call-timeout, 기본 30s |

base URL은 `/v1`을 포함한다. API의 completionsPath를 `/chat/completions`로 지정해 최종 요청이 `/v1/chat/completions`가 되도록 한다. 이 프로젝트가 고정한 Spring AI 2.0.0-M4의 기본 경로 `/v1/chat/completions`를 그대로 쓰면 `/v1`이 중복될 수 있다. 다른 경로의 호환 서비스를 사용할 때도 base URL에 API prefix를 포함한다.

개발 컨테이너 터미널에서 실행한다. `.env.example`은 자동 로드되지 않으므로 실제 API 키는 환경변수 또는 별도 비밀값 관리로 제공한다.

```sh
cd /workspaces/doqsa/backend
export OCR_PROVIDER=openai
export OPENAI_API_KEY='<OpenAI API key>'
./gradlew bootRun
```

Grok으로 실행할 때는 다음을 사용한다.

```sh
cd /workspaces/doqsa/backend
export OCR_PROVIDER=grok
export XAI_API_KEY='<xAI API key>'
export GROK_OCR_MODEL=grok-2-vision-1212
./gradlew bootRun
```

기본 모델명은 구현 요구사항에 맞춰 유지했다. 모델 제공 상태·권한은 API 계정과 시점에 따라 달라질 수 있다. 실제 배포 전 공급자 콘솔에서 이미지 입력을 지원하는 사용 가능 모델을 확인하고 `OPENAI_OCR_MODEL` 또는 `GROK_OCR_MODEL`로 변경한다. OpenAI의 현재 gpt-4o-mini 문서에는 deprecation 표시가 있다. xAI의 현재 예제는 더 최신 모델과 Responses API를 안내하지만 이번 구현은 요청한 OpenAI 호환 Chat Completions 경로를 사용한다.

연결·읽기 제한 시간은 선택한 공급자 timeout을 적용한다. SDK의 내부 재시도는 0회로 설정하고 영속 OCR 큐가 시도 한도·백오프를 관리한다. 통신 실패·빈 응답·구조화 변환 실패는 `OcrExceptions.Retryable`로 감싸며 응답에 공급자 오류 본문을 직접 노출하지 않는다. 기존 Gemini와 같이 모든 공급자 예외를 재시도 대상으로 처리하므로 영구 오류 세분화는 후속 개선 대상이다.

**검증.** 개발 컨테이너에서 `./gradlew clean test bootJar`로 전체 회귀 테스트와 문서 포함 JAR 빌드를 실행한다. 특정 신규 테스트는 아래처럼 실행할 수 있다.

```sh
./gradlew test --tests '*OpenAiCompatibleOcr*' --tests '*OcrProviderSelectionTest'
```

신규 테스트는 두 공급자 각각에 대해 영수증 필드·null 보존, 원본 이미지·MIME·프롬프트, 빈 응답·잘못된 JSON·공급자 예외, provider 선택·필수 설정 검증을 확인한다. 로컬 HTTP 모의 서버로 실제 Spring AI 요청의 인증 헤더·모델·단일 v1 경로·Base64 이미지·결과 변환, 503에서 중복 재시도하지 않는 동작, 읽기 timeout도 확인한다. 전체 Spring 자동설정 기동 테스트는 none·openai·grok에서 분석기 한 개와 선택된 공급자의 키만 필요함을 검증한다.

유료 외부 API 호출이나 실제 영수증 인식 정확도 평가는 수행하지 않는다. 기존 Supabase 외부 통합 테스트는 전용 환경변수가 없으면 건너뛴다.

근거: [고정 버전 OpenAiApi](https://github.com/spring-projects/spring-ai/blob/v2.0.0-M4/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/api/OpenAiApi.java), [고정 버전 OpenAiChatModel](https://github.com/spring-projects/spring-ai/blob/v2.0.0-M4/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java), [OpenAI GPT-4o mini](https://developers.openai.com/api/docs/models/gpt-4o-mini), [xAI Chat Completions](https://docs.x.ai/developers/model-capabilities/legacy/chat-completions).
