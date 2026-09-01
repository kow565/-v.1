# Harin — Perchance AI Companion (Android)

Instagram DM/Story 느낌의 개인용 AI companion Android 앱 프로토타입입니다.

## 핵심 기능

- DM 스타일 채팅 UI
- Perchance 텍스트 생성 서비스에 직접 연결해 한국어 캐릭터 답장 생성
- 대화마다 `outfit / pose / location / mood / hair / accessories / lighting` 상태를 구조화해 저장
- 이미지 프롬프트에 고정 성인 캐릭터 identity + 현재 상태를 매번 재사용
- 같은 anchor seed를 사용해 이미지 사이 인물 일관성을 높임
- 자연스러운 순간에는 대화 중 사진을 자동 생성
- Instagram Story처럼 상단 원형 스토리와 전체화면 뷰어 제공
- 백그라운드 JobScheduler가 선톡과 스토리를 독립적으로 자동 생성
- 선톡 약 55~245분 랜덤, 스토리 약 5~14시간 랜덤
- 새벽 01:00~08:00은 자동 활동을 쉬도록 설정
- 알림으로 선톡/새 스토리 표시

## Perchance 연결 방식

Perchance는 공식 public REST API를 제공하지 않으므로, 앱은 현재 웹앱이 사용하는 비공식 엔드포인트를 사용합니다.

- Text: `https://text-generation.perchance.org/api/verifyUser` + `/generate`
- Image: `https://image-generation.perchance.org/api/verifyUser` + `/generate`

이 엔드포인트는 Perchance 내부 구현 변경에 따라 깨질 수 있습니다. `AiEngine.java`에 통신이 분리되어 있어 이후 교체하기 쉽게 만들었습니다.

## 빌드

저장소의 `Build AI Companion APK` GitHub Actions가 APK를 생성합니다.

로컬에서는 JDK 17 + Android SDK 35 + Gradle 9.5 환경에서:

```bash
cd ai-companion
gradle :app:assembleDebug
```

결과: `app/build/outputs/apk/debug/app-debug.apk`

## 주의

Perchance 서비스에 과도한 요청을 보내지 않도록 자동 이미지/스토리 빈도를 낮게 설정했습니다. 백그라운드 실행 시 Android 제조사별 절전 정책 때문에 실행 시점이 약간 늦어질 수 있습니다.
