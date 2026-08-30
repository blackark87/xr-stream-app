# DMM WebView/WSD 수정 인계 문서

> 핵심 결론: 앱 빌드에 계정·JWT·OAuth/API secret을 넣는 구조를 제거했고, WebView 로그인 세션을 사용하는 최소 경로로 바꿨습니다. DEX/SO도 Git에 포함하므로 다른 PC에서 APK를 다시 프로비저닝할 필요는 없습니다. 다만 이 경로가 DMM과 WSD 서버 정책상 실제 허용되는지는 물리 헤드셋에서 확인해야 합니다.

## 이번에 한 일

| 영역 | 변경 내용 | 관련 파일 |
|---|---|---|
| 로그인 | 일반 DMM 구매 목록 WebView에서 사용자가 직접 로그인하도록 변경 | `DmmPanel.kt`, `DmmRepository.kt` |
| 비밀 설정 | DMM OAuth client/secret, JWT key, Digital API/HMAC 설정 제거 | `app/build.gradle.kts`, `secrets.local.properties.example` |
| 네이티브 API | OAuth/token/session store/구매 목록/playable-provider 클라이언트 삭제 | 삭제된 `DmmAuthClient.kt`, `DmmConfig.kt`, `DmmDigitalApiClient.kt`, `DmmSessionStore.kt` |
| 다운로드 | WebView의 Cookie, User-Agent, Referer를 사용하고 Range 재개 지원 | `DmmRepository.kt` |
| 스트리밍 | `.wsdcf` navigation/query URL을 감지해 WSD로 전달 | `DmmRepository.kt`, `DmmPanel.kt` |
| 권리 | WSD Rights-Issuer 결과를 동일 WebView에 열고 license callback을 WSD에 반환 | `DmmRepository.kt`, `DmmWsdRuntime.kt` |
| 재생 | WSD loopback URI를 기존 `PlaybackSource.Direct`/Media3로 전달 | `DmmWsdRuntime.kt`, 기존 player 코드 |
| 오프라인 | 앱 전용 Movies 저장소 다운로드, 파일 import, 다운로드 카드 재생 | `DmmRepository.kt`, `DmmPanel.kt` |
| 런타임 배포 | WSD DEX/SO ignore 제거 및 Git 추적, 프로비저닝 스크립트는 갱신용으로 유지 | `.gitignore`, `scripts/provision-dmm-runtime.sh` |
| 개인정보 노출 방지 | 브라우저 위치 표시와 Logcat URL에서 query/fragment를 제거하고 쿠키/토큰을 로그하지 않음 | `DmmPanel.kt`, `DmmRepository.kt` |

## 다른 PC에서 필요한 것

새 PC에는 이 저장소 clone과 일반 Android 개발 환경만 있으면 됩니다. 원본 DMM APK를 복사하거나 다음 DMM 값을 properties에 넣을 필요가 없습니다.

- 계정 또는 비밀번호
- JWT/token
- OAuth client ID/secret/redirect
- Digital API auth secret
- `dmm_app_uid` 또는 `secid`

사용자는 설치된 앱 안의 WebView에서 직접 로그인합니다. 로그인 값은 Git에 들어가지 않습니다.

저장소에 포함한 런타임은 다음과 같습니다.

| 파일 | 크기(bytes) | SHA-256 |
|---|---:|---|
| `app/src/main/assets/dmm/wsd-runtime.dex` | 7,788,128 | `f98be3318228f001f29664ac7d8c6dd6cfea6b3229a7a8639b974a9a384ab201` |
| `app/src/main/jniLibs/arm64-v8a/libwsdnat.so` | 43,368 | `6c22660e076bbf7289c303e2a2fda45c1ac9cdc640f08712253e19db35af581a` |
| `app/src/main/jniLibs/arm64-v8a/libwsdprtn.so` | 1,086,088 | `2e84f2dcdc25f865a974dbbb97a66f11e307f7d4c026122f95884e1de46660ca` |

`scripts/provision-dmm-runtime.sh`는 런타임 버전을 바꿀 때만 실행합니다. 실행 후 세 바이너리 diff와 해시를 검토해 의도적인 업데이트일 때만 커밋합니다.

## 정적으로 확인한 것

- DMM용 BuildConfig secret과 native OAuth/API client 참조가 제거됐습니다.
- WebView CookieManager에서 받은 쿠키를 다운로드 HTTP 요청에 전달합니다.
- 권리 페이지는 로그인에 쓴 동일 WebView 인스턴스를 사용합니다.
- WSD DEX에는 `WsdVideoInteraction`과 `WsdRightsAcquiringSession`이 함께 들어 있습니다.
- 두 SO는 ARM64 ELF이고 파일 해시는 위 표와 일치합니다.
- WSD callback URI는 기존 Media3 direct playback 경로에 연결돼 있습니다.
- 저장소 diff와 셸 스크립트, XML, 바이너리 형식 및 민감 설정 잔존 여부를 비컴파일 정적 검사했습니다.

## 아직 확인하지 못한 것

다음은 Ubuntu 서버의 정적 분석만으로 성공 여부를 알 수 없습니다.

- DMM 로그인이 이 헤드셋 WebView에서 완료되는지
- 사이트의 Stream/Download가 `DownloadListener` 또는 일반 URL navigation으로 노출되는지
- CDN 다운로드가 Cookie/User-Agent/Referer만으로 승인되는지
- WSD 내부 HTTP 클라이언트가 원격 스트림 URL을 직접 읽을 수 있는지
- 일반 웹 세션만으로 새 앱 패키지의 WSD 권리를 발급받을 수 있는지
- WSD 권리가 이 앱 설치본에 정상 저장되고 재사용되는지
- WSD loopback URI가 Media3에서 재생·seek되는지

특히 `dmm_app_uid` 문제는 아직 “필요 없다”로 결론 난 것이 아닙니다. 웹 로그인 후 권리 단계가 거절되면 그 값, 원 앱 전용 SessionID 브리지, 패키지 등록 중 하나가 실제 필수일 가능성이 커집니다.

## 물리 헤드셋 확인 순서

1. Android Studio에서 main branch를 build합니다.
2. ARM64 Android XR 헤드셋에 앱을 설치합니다.
3. **DMM / FANZA** 패널을 열고 WebView에서 로그인합니다.
4. 구매 목록이 보이고 다시 열어도 세션이 유지되는지 확인합니다.
5. 구매 콘텐츠의 **Download**를 눌러 `download_intercepted`와 진행률, 완료 카드를 확인합니다.
6. 완료 카드에서 **Play offline**을 누릅니다.
7. 권리 페이지가 나타나면 정상 인증을 마치고 `rights_response` 및 `wsd_ready`를 확인합니다.
8. 영상 재생, seek, 일시정지, 플레이어 종료 후 재진입을 확인합니다.
9. 같은 콘텐츠의 사이트 **Stream** 동작도 확인합니다.
10. 앱을 재시작하고 다운로드 및 기존 권리가 유지되는지 확인합니다.

프로젝트 정책상 이 작업에서는 Gradle build/test/install과 ADB 기기 테스트를 실행하지 않았습니다.

## 로그 수집

사용자 개발 PC에서 다음 명령을 실행합니다.

```bash
adb logcat -c
adb logcat -s DmmWebFlow
```

관련 이벤트:

```text
web_login_open
web_page_finished
download_intercepted
download_complete
stream_link_intercepted
wsd_open
rights_required
license_url_intercepted
rights_response
wsd_ready
flow_error
```

로그 코드가 query/fragment를 제거하도록 되어 있어도 공유 전에는 쿠키, token, `licenseUID`, 계정 식별자가 없는지 다시 확인합니다. 화면 녹화에도 로그인 정보가 포함되지 않도록 합니다.

## 실패 지점별 다음 수정

| 마지막 이벤트/증상 | 의미 | 다음 수정 후보 |
|---|---|---|
| 로그인 페이지에서 더 진행되지 않음 | 임베디드 WebView 정책 또는 인증 UI 문제 | Custom Tabs/외부 브라우저 세션 전달 가능성 검토. 단, 쿠키 공유 제한 확인 필요 |
| 로그인 완료, 다운로드 이벤트 없음 | 사이트가 JS `blob:` 또는 앱 전용 브리지를 사용 | `shouldInterceptRequest` 관찰 또는 최소 JS blob bridge 추가 |
| `download_intercepted` 후 HTTP 401/403 | CDN 인증 문맥 부족 | redirect별 쿠키 갱신 또는 앱 내부 인증 Range 프록시 검토 |
| `wsd_open` 후 원격 스트림만 실패 | WSD 내부 client와 WebView cookie가 분리됨 | WebView 인증을 적용하는 local Range proxy를 WSD 입력으로 제공 |
| `rights_required` 후 권리 페이지가 로그인으로 돌아감 | WSD request와 WebView session 연결 부족 | Rights-Issuer cookie 요구를 기기 트래픽에서 확인 |
| `rights_response`가 계속 거절됨 | 원 앱 SessionID/`dmm_app_uid`/package 정책 가능성 | 민감값을 복사하지 말고 정상 웹 응답과 원 앱 정상 흐름의 필수 입력만 비교 |
| `wsd_ready` 후 플레이어 오류 | loopback/Media3/lifecycle 문제 | loopback HTTP Range, URI 수명, facade close 시점을 확인 |

기기 로그 없이 여러 우회 경로를 한꺼번에 추가하지 말고 마지막 성공 이벤트 다음 한 단계만 보강하는 것이 안전합니다.

## 완료 판단 기준

이 기능은 다음 네 항목이 모두 실제 헤드셋에서 재현될 때 완료로 판단할 수 있습니다.

- WebView 로그인과 구매 페이지 접근
- 한 콘텐츠의 직접 스트리밍 재생
- 암호화 WSDCF의 기기 다운로드와 완료 카드 표시
- 네트워크를 끈 뒤 기존 유효 권리로 동일 다운로드 재생

현재 커밋은 이 검증을 위한 구현과 관찰 지점을 제공하지만, 위 네 항목의 완료를 주장하지 않습니다.
