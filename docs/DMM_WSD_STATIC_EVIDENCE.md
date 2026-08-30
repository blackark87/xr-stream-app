# DMM/WSD 오프라인 정적 증거 요약

이 파일은 이식 판단에 사용한 원 APK 분석 결과를 비밀값 없이 보존한 저장소 내부 요약이다. 원본 APK 전체, 계정 정보, 토큰, client secret, HMAC key는 저장소에 포함하지 않는다. 따라서 원 APK에서의 추출 자체를 반복하려면 저장소 소유자가 합법적으로 보유한 동일 APK가 별도로 필요하지만, 보고서가 참조하는 결론과 현재 코드와의 비교 지점은 이 파일만으로 읽을 수 있다.

## WSD 권리와 loopback 경계

- WSD 공개 클래스 경계: `jp.co.webstream.drm.android.pub.WsdVideoInteraction`
- 권리 세션 경계: `jp.co.webstream.drm.android.pub.WsdRightsAcquiringSession`
- 콘텐츠 open 결과가 권리 미보유이면 `onAcquireRights` callback으로 Rights-Issuer 입력이 전달된다.
- 권리 세션은 첫 요청과 후속 라이선스 URL 요청을 구분한다.
- 정상 권리 확인 뒤 `onRightsChecked` callback이 로컬 HTTP Range 재생 URI를 반환한다.
- target 앱은 이 URI를 `PlaybackSource.Direct`로 기존 Media3 플레이어에 전달한다.
- 이 경계는 콘텐츠 키를 앱 코드로 추출하지 않으며 WSD 실패를 우회하지 않는다.

저장소에 포함된 런타임 식별값:

| 파일 | SHA-256 |
|---|---|
| `app/src/main/assets/dmm/wsd-runtime.dex` | `f98be3318228f001f29664ac7d8c6dd6cfea6b3229a7a8639b974a9a384ab201` |
| `app/src/main/jniLibs/arm64-v8a/libwsdnat.so` | `6c22660e076bbf7289c303e2a2fda45c1ac9cdc640f08712253e19db35af581a` |
| `app/src/main/jniLibs/arm64-v8a/libwsdprtn.so` | `2e84f2dcdc25f865a974dbbb97a66f11e307f7d4c026122f95884e1de46660ca` |

## 원 앱의 SessionID 경계

원 앱의 native 인증 경로에서는 다음 매핑이 확인됐다.

```text
POST /connect/v1/issueSessionId
response.unique_id -> dmm_app_uid cookie
response.secure_id -> secid cookie
```

이것은 현재 web-only 구현이 해당 값을 생성한다는 의미가 아니다. WebView 정상 로그인 세션만으로 WSD 권리 발급이 실패할 경우 비교해야 할 원 앱의 경계로 보존한다.

## 원 앱의 native API 경계

원 앱의 IL2CPP 정적 분석에서는 다음 native 경로가 확인됐다.

```text
GET /purchase/list/vr
GET /playableprovider/stream/vr
GET /playableprovider/download/vr
```

playable-provider 요청은 원 앱에 허가된 인증 문맥과 HMAC 입력을 사용했다. 실제 비밀키는 추출하거나 기록하지 않았다. 최초 포트는 이 native 경로를 복제하려 했지만, 현재 커밋에서는 모두 제거하고 DMM 사이트 자체의 구매 페이지와 stream/download 동작을 사용한다.

## 현재 구현과의 비교

| 원 앱 경계 | 현재 target 구현 | 상태 |
|---|---|---|
| 앱 OAuth/token/JWT | 일반 DMM WebView 로그인 | 코드 연결됨, 기기 미검증 |
| `issueSessionId`와 쿠키 주입 | 기존 WebView 세션으로 Rights-Issuer 진행 시도 | 기기 미검증 |
| native 구매 목록 API | DMM 사이트 구매 페이지 | 기기 미검증 |
| playable-provider URL 발급 | 사이트가 노출하는 WSDCF URL 감지 | 기기 미검증 |
| WSD rights/loopback | 번들 DEX/SO reflection bridge | 정적 연결 확인, 기기 미검증 |
| Unity 렌더러 | 기존 Jetpack XR Media3 플레이어 | 정적 연결 확인, 기기 미검증 |

정적 분석으로 확정할 수 없는 항목을 성공으로 표시하지 않는다. 실제 성공 여부는 [DMM WebView/WSD 수정 인계 문서](./DMM_WEBVIEW_HANDOFF.md)의 물리 헤드셋 순서로 확인한다.
