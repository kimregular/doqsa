**Supabase Storage 개발 환경.** `SupabaseImageStorage`는 기존 `ImageStorage` 포트를 구현한다. `STORAGE_PROVIDER=supabase`일 때만 활성화되며, 다른 저장소를 선택하면 Supabase 설정과 자격증명은 필요하지 않다.

| 프로퍼티 | 환경변수 | 설명 |
|---|---|---|
| `doqsa.storage.supabase.endpoint` | `SUPABASE_STORAGE_ENDPOINT` | 서버가 HEAD/GET에 사용하는 S3 API URL, 필수 |
| `doqsa.storage.supabase.public-endpoint` | `SUPABASE_STORAGE_PUBLIC_ENDPOINT` | 클라이언트용 URL 서명 주소. 생략하면 endpoint 사용 |
| `doqsa.storage.supabase.bucket` | `SUPABASE_STORAGE_BUCKET` | 미리 생성한 비공개 버킷, 필수 |
| `doqsa.storage.supabase.access-key` | `SUPABASE_STORAGE_ACCESS_KEY` | S3 Access Key ID, 필수 |
| `doqsa.storage.supabase.secret-key` | `SUPABASE_STORAGE_SECRET_KEY` | S3 Secret Access Key, 필수 |
| `doqsa.storage.supabase.region` | `SUPABASE_STORAGE_REGION` | 프로젝트 region, 기본 local |
| `doqsa.storage.supabase.url-validity` | Spring의 표준 설정 바인딩 사용 | Presigned PUT/GET 만료, 기본 10m, 1초~7일 |
| `doqsa.storage.supabase.call-timeout` | Spring의 표준 설정 바인딩 사용 | SDK API 호출·시도 제한, 기본 30s |

호스팅된 Supabase는 Storage 설정의 S3 endpoint와 region을 사용한다. 예: `https://<project-ref>.storage.supabase.co/storage/v1/s3`. Docker의 Storage API에 직접 접근할 때는 `/s3`이며, 전체 Supabase 게이트웨이 경로 `/storage/v1/s3`와 구분한다. S3 클라이언트와 presigner 모두 path-style access를 사용한다.

S3 Access Key/Secret Key는 REST API용 service_role JWT나 Supabase API secret key와 다르다. 운영 자격증명은 서버에서만 보관하고 클라이언트에는 특정 객체·동작·기간에 제한된 URL만 전달한다. S3 키는 RLS를 우회하므로 URL 발급 전 사용자·작업 권한 검사는 API가 담당해야 한다.

**로컬 Docker 실행.** 아래 명령은 저장소 루트에서 실행한다. `supabase` 프로파일은 PostgreSQL과 실제 `supabase/storage-api:v1.89.12`를 실행한다. Storage의 file 백엔드가 S3 프로토콜을 제공하므로 별도 MinIO·AWS 계정·Supabase 클라우드 프로젝트는 필요하지 않다. DB와 파일은 각각 named volume에 남는다. 컨테이너의 샘플 자격증명은 로컬 개발 전용이며 호스트 포트는 loopback에만 노출한다.

```sh
docker compose -p doqsa-supabase -f .devcontainer/docker-compose.yml --profile supabase up -d --wait storage-db supabase-storage
docker compose -p doqsa-supabase -f .devcontainer/docker-compose.yml run --rm storage-init
```

초기화 작업은 만료가 짧은 개발용 service_role JWT를 생성해 REST API로 `doqsa-images` 비공개 버킷을 만든다. 기존 버킷이면 재사용하고, 공개 버킷이면 실패한다. 버킷은 JPEG·PNG·WebP와 10MiB 한도로 구성한다. 기본 local 모드로 개발할 때는 이 프로파일을 시작할 필요가 없다.

개발 컨테이너 안에서 다음과 같이 실행한다. Compose의 app 환경에는 내부·공개 주소와 로컬 자격증명이 이미 설정되어 있다.

```sh
cd /workspaces/doqsa/backend
STORAGE_PROVIDER=supabase ./gradlew bootRun
```

내부 SDK 주소는 `http://supabase-storage:5000/s3`, 호스트 클라이언트용 서명 주소는 `http://localhost:5000/s3`다. 포트를 바꾸려면 Compose 실행 시 `SUPABASE_STORAGE_PORT`를 설정한다. 물리 iPhone이나 다른 컴퓨터에서 접근하려면 접근 가능한 호스트 주소와 포트 바인딩을 별도로 설정해야 한다. URL을 서명한 뒤 문자열 치환으로 호스트를 바꾸면 서명이 깨지므로 `public-endpoint`에서 처음부터 올바른 주소로 서명한다.

웹 브라우저 교차 출처 업로드에는 CORS가 추가로 필요하다. 이 최소 구성은 직접 HTTP·네이티브 클라이언트 테스트용이며, 브라우저용 게이트웨이와 OPTIONS 처리는 포함하지 않는다. Supabase의 S3 PutBucketCors API는 지원되지 않으므로 S3 버킷 CORS 설정으로 해결하려고 해서는 안 된다.

**테스트.** 일반 `./gradlew test`는 외부 서비스 없이 단위·설정 테스트를 실행한다. 실제 Storage 왕복 테스트는 `SUPABASE_STORAGE_TEST_ENDPOINT`가 있을 때 실행하며, 없으면 건너뛴다. 다음 명령은 반드시 위 초기화 이후 저장소 루트에서 실행한다. 모든 Java 빌드·테스트는 개발 컨테이너 안에서 실행된다.

```sh
docker compose -p doqsa-supabase -f .devcontainer/docker-compose.yml run --rm --no-deps --user vscode --workdir /workspaces/doqsa/backend \
  -e SUPABASE_STORAGE_TEST_ENDPOINT=http://supabase-storage:5000/s3 \
  -e SUPABASE_STORAGE_TEST_PUBLIC_ENDPOINT=http://supabase-storage-public:5000/s3 \
  -e SUPABASE_STORAGE_TEST_ACCESS_KEY=doqsa-local-access \
  -e SUPABASE_STORAGE_TEST_SECRET_KEY=doqsa-local-secret-development-only \
  app ./gradlew clean test bootJar
```

테스트는 실제 PUT 서명·업로드, HEAD, SDK GET, Presigned GET, 같은 key의 URL 재발급·재업로드를 검증하고 생성 객체를 정리한다. 서로 다른 내부·공개 DNS 별칭을 사용해 두 endpoint 설정을 함께 검증한다. 로컬 자격증명을 바꿨다면 테스트 환경변수도 같은 값으로 전달해야 한다. 다른 환경에는 bucket과 region도 `SUPABASE_STORAGE_TEST_BUCKET`, `SUPABASE_STORAGE_TEST_REGION`으로 지정할 수 있다.

개발 서비스를 중지하되 데이터는 유지하려면 다음을 사용한다.

```sh
docker compose -p doqsa-supabase -f .devcontainer/docker-compose.yml --profile supabase down
```

이 어댑터는 기존 업로드→업로드 확인→OCR 흐름을 유지한다. 물리 삭제·원본 불변성·실제 이미지 콘텐츠 검증은 기존 포트의 범위와 같으며 별도로 구현해야 한다. 스토리지 provider·bucket을 바꾸면 기존 DB의 key만으로 객체를 찾을 수 없으므로 기존 이미지 이전이나 provider별 데이터 분리도 고려해야 한다.

근거: [Supabase S3 인증](https://supabase.com/docs/guides/storage/s3/authentication), [S3 호환 범위](https://supabase.com/docs/guides/storage/s3/compatibility), [S3 endpoint와 저장 백엔드의 구분](https://supabase.com/docs/guides/self-hosting/self-hosted-s3), [고정 버전 설정](https://github.com/supabase/storage/blob/v1.89.12/src/config.ts).
