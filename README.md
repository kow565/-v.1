# KLAS Watch (Android)

광운대학교 KLAS 공지를 **PC 없이 Android 휴대폰에서 주기적으로 확인**하고, 새 공지를 발견하면:

1. 휴대폰 로컬 알림을 띄우고
2. Google Apps Script 웹훅을 통해 **본인 Gmail로 정형화된 메일**을 보내
3. ChatGPT의 연결된 Gmail/중요 메일 자동화가 읽을 수 있게 하는 개인용 앱입니다.

## 현재 버전

- v0.1.0
- KLAS 비밀번호를 앱 코드/설정에 저장하지 않음
- WebView에서 사용자가 직접 로그인하고 WebView 쿠키 세션을 재사용
- Android WorkManager 기반 15/30/60분 감시
- 최초 검사 시 기존 공지는 기준선(baseline)으로만 저장
- 새 공지만 휴대폰 알림 + Gmail 중계
- 공지 제목 키워드 기반 `과제 / 시험 / 휴강·보강 / 준비물 / 마감 / 공지` 분류
- 공지 상세 페이지에서 본문을 최대 5,000자 추출하여 Gmail에 포함
- 로그인 세션 만료 감지 시 재로그인 알림

## 빌드

GitHub Actions가 `main` 브랜치에 push될 때 자동으로 debug APK를 빌드하고 `KLASWatch-debug-apk` artifact로 저장합니다.
