# 초기 구현

* [✅] JPA OCR 도메인과 작업 생성·조회·삭제·업로드 확인
* [✅] 이미지 저장소 포트와 로컬·S3 어댑터
* [✅] Supabase S3 어댑터·개발용 Docker 구성·실제 Storage 왕복 테스트
* [✅] Gemini OCR 어댑터와 영수증 프롬프트
* [✅] Spring 비동기 큐, DB 폴링, 선점·lease·재시도·정상 종료
* [✅] 공통 오류 처리·추적 로깅·OpenAPI·REST Docs
* [✅] 개발 컨테이너 빌드 및 기능·동시성·복구 테스트

검증: 개발 컨테이너에서 `./gradlew clean test bootJar` 성공, 테스트 24개 통과.
실행 JAR의 HTTP 업로드·조회·삭제와 OpenAPI·REST Docs 접근을 확인했다.
Gemini·S3 실제 외부 호출은 자격증명 설정 후 별도 검증이 필요하다.

인증, 운영 DB 마이그레이션, iOS, 외부 MQ, FCM 등의 별도·추후 작업 범위는 루트 README.md를 따른다.

Supabase 추가 검증: 개발 컨테이너에서 실제 Supabase Storage v1.89.12와 PostgreSQL을 실행해
`./gradlew clean test bootJar` 성공, 테스트 35개 통과(실제 Storage 통합 테스트 1개 포함).
비공개 버킷 초기화·재실행, Presigned PUT/GET, HEAD, SDK 읽기, URL 재발급을 확인했다.
실행 방법은 [Supabase Storage 개발 가이드](docs/supabase-storage.md)를 따른다.
