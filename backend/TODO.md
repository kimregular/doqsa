# 초기 구현

* [✅] JPA OCR 도메인과 작업 생성·조회·삭제·업로드 확인
* [✅] 이미지 저장소 포트와 로컬·S3 어댑터
* [✅] Gemini OCR 어댑터와 영수증 프롬프트
* [✅] Spring 비동기 큐, DB 폴링, 선점·lease·재시도·정상 종료
* [✅] 공통 오류 처리·추적 로깅·OpenAPI·REST Docs
* [✅] 개발 컨테이너 빌드 및 기능·동시성·복구 테스트

검증: 개발 컨테이너에서 `./gradlew clean test bootJar` 성공, 테스트 24개 통과.
실행 JAR의 HTTP 업로드·조회·삭제와 OpenAPI·REST Docs 접근을 확인했다.
Gemini·S3 실제 외부 호출은 자격증명 설정 후 별도 검증이 필요하다.

인증, 운영 DB 마이그레이션, iOS, 외부 MQ, FCM 등의 별도·추후 작업 범위는 루트 README.md를 따른다.
