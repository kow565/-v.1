# ARCA · Chat RPG Android

한국어로 자유롭게 행동을 입력하는 개인용 AI RPG. 세계관과 캐릭터를 만들고, 이야기·성장·아이템·동료 기록을 휴대폰에서 이어갈 수 있습니다.

## 설치와 시작

1. `ChatRPG-v0.1.0.apk`를 Android 8.0 이상 및 최신 Android System WebView를 사용하는 휴대폰에 내려받아 설치합니다. Android가 요청하면 다운로드에 사용한 앱의 외부 앱 설치 권한을 허용합니다.
2. `새로운 모험 시작` → `연습 모드`를 선택하면 API 키 없이 고정된 작은 판타지 이야기를 플레이할 수 있습니다.
3. 자유로운 GPT 모험은 설정에서 본인의 OpenAI API 키와 모델을 입력한 뒤 새 `AI 모드` 모험을 만듭니다. 기본 모델 이름은 `gpt-6.1-sol`이며 계정에서 사용할 수 있는 모델로 변경할 수 있습니다.

**API 사용료는 ChatGPT 구독과 별도입니다.** 이 APK는 API 키나 API 이용료를 포함하지 않습니다. 서버 없이 휴대폰에서 OpenAI Responses API에 직접 HTTPS 요청합니다. 키는 Android Keystore로 암호화해 기기에 저장하며, 소스·세이브 내보내기에 포함하지 않습니다.

## 기능

- 판타지 / 현대 헌터 / 생존 배경 및 사용자 세계관 설명
- 이름·직업 설정, 자유 행동, AI의 3개 추천 행동
- 체력·마력·골드·경험치·레벨·가방·동료·현재 목표와 서사 요약
- 기기에서 난수 d20 생성, 상태 변경 제안 검증과 범위 제한
- 응답 검증·저장 성공 후 턴 반영, 실패 후 입력 유지, 중복 전송 방지
- 최대 8개 모험 자동 저장, JSON 내보내기·불러오기
- 누적 응답 토큰 표시; 최근 12개 대화와 요약만 API에 전송

AI가 서사와 전투 결과를 제안하고 앱이 수치 범위를 검증하는 방식입니다. 엄격한 D&D 규칙 엔진은 아닙니다. 연습 모드는 GPT를 호출하지 않는 고정 이야기이며 자유 생성은 AI 모드에서 제공합니다. 텍스트 중심 첫 버전으로 이미지·음성·온라인 멀티플레이는 포함하지 않습니다.

## 빌드

Java 17 런타임의 컴파일러 모듈, Python 3, Android SDK platform35와 build-tools35.0.0이 필요합니다. Gradle 또는 외부 Android 라이브러리에 의존하지 않습니다.

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0"
export ANDROID_SDK_ROOT=/path/to/android-sdk
python3 scripts/build-apk.py
```

출력: `build/ChatRPG-v0.1.0.apk`. 처음 빌드하면 `.signing/debug.jks`에 **개발용** 서명 키를 생성합니다. 같은 설치에 업데이트하려면 동일 키를 유지해야 합니다. 소스 저장소에는 키를 올리지 않습니다. CI의 새 빌드는 별도 개발 서명을 생성하므로 로컬 APK 위에 업데이트되지 않을 수 있습니다. 배포용 서명과 공개 배포 기능은 이 개인용 MVP 범위 밖입니다.

GitHub Actions는 `chat-rpg` 브랜치 push 또는 수동 실행 시 네이티브/JS 테스트 후 APK를 빌드하고 artifact로 올립니다.

## 검증

```sh
npm install --ignore-scripts
npm test
mkdir -p build/native-tests
javac --release 8 -d build/native-tests app/src/main/java/dev/ojun/chatrpg/ProtocolSafety.java tests/native/ProtocolSafetyTest.java
java -cp build/native-tests dev.ojun.chatrpg.ProtocolSafetyTest
```

`javac` 실행 파일이 없지만 JDK 컴파일러 모듈이 설치되어 있다면 `java com.sun.tools.javac.Main`을 사용할 수 있습니다.

웹 UI smoke test는 개발 환경의 Playwright와 Chromium이 필요합니다. `CHROME_EXECUTABLE`로 기존 Chromium 실행 파일 경로를 지정할 수 있습니다.

```sh
node tests/ui-smoke.cjs
```

실제 API 키가 제공되지 않은 제작 환경에서는 실제 계정의 GPT 생성 결과를 검증하지 않았습니다. Android 기기/에뮬레이터에서 Keystore·파일 선택기 실행은 설치 후 확인이 필요합니다. APK 빌드·서명·정렬 및 게임 상태/오류 처리 테스트를 별도로 검증합니다.

## 코드 구조와 참고 프로젝트

- `app/src/main/assets/core.js`: 상태 검증, 턴 적용, 연습 이야기, API 요청/응답 계약
- `app/src/main/assets/ui.js`: 한국어 모바일 UI, 모험 목록, 자동 저장
- `app/src/main/java/dev/ojun/chatrpg/MainActivity.java`: WebView, 암호화 설정, HTTPS, 앱 전용 저장, Android 파일 선택기
- `scripts/build-apk.py`: 리소스·Java·DEX 패키징과 서명

[RPG LLM Adventure](https://github.com/ClaudioDrews/rpg-llm-adventure)의 MIT 공개 소스를 확인하고, 이야기 프롬프트 일부 구조를 한국어로 수정해 활용했습니다. 해당 프로젝트 전체를 포크한 것이 아니라 Android 앱에 맞춘 새 구현입니다. 원본 저작권·허가문은 `THIRD_PARTY_NOTICES.txt` 및 앱에 포함되어 있습니다.

기존 `kow565/-v.1` 저장소의 다른 앱은 `main`에 유지하고 이 프로젝트는 독립 `chat-rpg` 브랜치에 저장합니다.
