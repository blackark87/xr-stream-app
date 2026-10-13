# Unity/OpenXR 포팅 소스

실제 Unity 프로젝트: `G:\workspace\unity-stream-app\unity-xr-stream`

이 디렉터리는 해당 프로젝트의 `Assets/XRStream` 및 `Assets/Plugins/Android/mainTemplate.gradle`에 반영하는 소스다. 원본 Android 앱은 `app/`, Unity용 Android 플러그인은 `unity-bridge/`에 있다.

## 현재 구현 범위

P1 실행 기반과 P2 영상 출력 후보를 구현했다. 전체 기능 포팅이나 Galaxy XR 검증 완료를 의미하지 않는다.

작업 중단 기준점과 다음 순서: [잔여 작업 목록](../docs/UNITY_OPENXR_REMAINING_TASKS.md). 최우선 미해결 문제는 seek preview의 SBS/TB 양안 출력이며, 현재 일반 RawImage에 원본 프레임 전체가 표시된다.

- 독립 앱 ID `blackark.app.vr.unity`, Unity/OpenXR의 단일 XR 세션.
- 컨트롤러 aim ray와 손 aim/pinch를 사용하는 world-space UI.
- SAF 폴더 선택·영속 권한, SMB 서버 추가·수정·연결 시험·삭제·폴더 탐색.
- 기존 Room 데이터 모델, Keystore 자격 증명 저장소와 JCIFS 코드를 재사용하는 AAR.
- Unity VideoPlayer → RenderTexture → URP 셰이더로 2D/180/360 및 Mono/SBS/TB 출력.
- SMB·SAF 파일을 loopback HTTP byte-range로 전달. seek 위치에 필요한 구간을 읽는다.
- 재생·일시정지·±10초·타임라인 seek·배속·음량·폴더 내 이전/다음 영상.
- 파일별 위치·형식·스테레오 저장, 이어보기, pause/resume, 3초 Recenter.
- 첫 재생 시 기존의 고해상도 2:1 프레임 추론 기준을 적용. 저장된 비기본 형식을 우선한다.
- 넓은 2단 재생 바와 별도 Display 패널.
- 한국어 외부 `.ko.srt`/`.ko.ass` 자막 자동 연결, 색상 span·켜기/끄기·seek 시 자막 동기화.
- 파일 내 오디오 트랙 수·언어 표시와 Display의 Audio 버튼으로 순환 선택.
- 자막 크기 4단계·거리·상하 위치와 UI 좌우 위치 조절·초기화·영속 저장.
- 원본처럼 95% 이상 본 영상은 재진입 시 처음부터 재생. 마지막 영상 종료 후 Play로 다시 재생.
- 한국어 파일명·서버명·오류 메시지를 표시하는 Noto Sans KR 메뉴 폰트.
- 조작 후 10초 자동 숨김·빈 공간 trigger/pinch로 재표시·슬라이더/seek 입력 잠금과 pause 시 조작 취소.
- 타임라인 drag 중 별도 무음 Unity 디코더가 작은 GPU texture로 출력하는 seek 미리보기.

현재 미이전: 내장 자막 트랙 선택, 시스템 자막 글꼴 목록·선택, FOV·헤드 추적 UI 설정과 나머지 플레이어 설정, 홈 이어보기 카드, NFO·그룹·상세·포스터·라이브러리 미리보기·빠른 접근·설정·YouTube. 검증용 메뉴는 영어이며 UI 언어 설정 이전도 남아 있다. Unity VideoPlayer의 모든 codec·색공간·양안 품질이 검증된 것은 아니다. 신규 자동 숨김·미리보기의 실기기 검증은 아직 남아 있다.

## 소스 반영

저장소 루트의 PowerShell에서 실행한다.

```powershell
.\gradlew.bat :unity-bridge:testDebugUnitTest :unity-bridge:exportUnityRuntime
.\tools\Sync-UnityPort.ps1
```

`Sync-UnityPort.ps1`은 XRStream 소스, 브리지 AAR 한 개와 Unity 6000.6 표준 기반 `mainTemplate.gradle`을 복사한다. Room·JCIFS·coroutines의 전이 의존성은 이 템플릿의 Maven 선언을 통해 Unity 의존성과 함께 해석한다. 이전 동기화에서 복사했던 의존성 AAR/JAR 18개와 해당 meta 파일만 정리하며 다른 플러그인은 삭제하지 않는다.

실행 중인 Editor에 Unity CLI로 연결한다. Editor를 열어 둔 프로젝트에 별도의 batch Editor를 실행하지 않는다.

```powershell
$unityCli = 'D:\util\unityHub\Unity Hub\resources\unity.exe'
$unityProject = 'G:\workspace\unity-stream-app\unity-xr-stream'
& $unityCli recompile --project-path $unityProject --json
& $unityCli command eval 'return XRStream.Editor.XRStreamSceneBuilder.CreateScene();' --project-path $unityProject --json
```

씬 생성기는 `Assets/XRStream/Scenes/XRStream.unity`를 만들고 빌드에 활성화한다. 기존 SampleScene 파일은 유지하며 빌드 목록에서는 비활성화한다. 현재 씬에 저장하지 않은 변경이 있으면 먼저 저장하라는 오류를 반환한다.

## Android 빌드

Editor의 활성 빌드 타깃을 Android로 전환한다. 아래 명령은 비동기이므로 완료 상태를 확인한 후 빌드한다.

```powershell
& $unityCli command switch_build_target --target Android --confirm true --project-path $unityProject --json
& $unityCli command switch_build_target_status --project-path $unityProject --json
& $unityCli command build --target Android --outputPath 'G:\workspace\xr-stream-app\build\unity-port\xr-stream-unity-p2.apk' --options '["Development","DetailedBuildReport"]' --scenes '["Assets/XRStream/Scenes/XRStream.unity"]' --confirm true --project-path $unityProject --json
& $unityCli command build_status --project-path $unityProject --json
```

빌드는 기기 설치를 수행하지 않는다. 로컬 도구 경로는 이 Windows 개발 환경 기준이다. 모듈·라이선스·SDK 오류가 발생하면 해당 조치를 사용자에게 알리고, 다른 프로젝트·플레이어·그래픽스 API로 우회하지 않는다.

중단 전 **Minimum 37 / Target 37**로 개발용 APK 빌드가 성공했다(오류 0, 경고 6, 119.3초). APK의 compile/min/target 37, 앱 ID `blackark.app.vr.unity`, ARM64, OpenXR loader 및 UnityBridge·Room 생성 클래스 포함을 확인했다. 경로는 `build/unity-port/xr-stream-unity-p2.apk`다.

첫 빌드의 compileSdk 36 불일치는 37 설정으로 해소했다. 브리지와 앱 namespace 중복, 로컬 AAR과 Maven AndroidX 의존성 중복은 브리지 전용 namespace와 Maven 기반 패키징으로 수정했다.

후속 진단에서 Unity 6000.6.5f1 Target 선택창 버그를 확인했다. Target 목록은 32부터 시작하지만 현재 Inspector의 변환 기준은 26이다. 표시된 37의 목록 번호 6을 31로 해석해 최소값으로 강제 보정한다. 설정 복사본은 34/37을 정상 유지했다. GUI에서 같은 값을 반복 선택하거나 SDK를 재설치할 문제가 아니다.

연결된 Galaxy XR `SM-I610`은 실제 Android 14/API 34였다. 실기기 설치 조건에 맞춰 공개 PlayerSettings API로 **Minimum 34 / Target 37**을 저장했고, `build/unity-port/xr-stream-unity-device.apk` 빌드에 성공했다(오류 0, 경고 10, 72.7초). APK의 compile 37/min 34/target 37·ARM64를 확인하고 별도 앱 ID로 설치·실행했다. 기기는 OpenXR 1.1·spatial API 3·손/컨트롤러 기능을 제공한다.

Target 37의 SMB 연결에는 `ACCESS_LOCAL_NETWORK` 선언과 런타임 요청을 추가했다. 거절 시 Android 앱 설정의 Nearby devices 권한을 허용하라는 오류를 표시한다. [Android 17 로컬 네트워크 권한](https://developer.android.com/privacy-and-security/local-network-permission)

UI 완성도 작업은 빌드·실제 영상 출력 검증 뒤에 진행한다.

## 실기기 진단 준비

개발용 빌드에는 `[XRStream]` 로그를 추가했다. 시작 시 OS·GPU·graphics API·색공간과 실제 XR display 실행 상태, 손/컨트롤러 추적·press와 UI hit, 영상 prepare·해상도·오디오 트랙 수·첫 프레임·seek 완료·projection·앱 pause를 기록한다. 첫 프레임 콜백은 한 번 받은 뒤 끄며 CPU로 영상 프레임을 읽지 않는다. `XRStreamBridge` 로그는 요청 종류·소요 시간·오류 종류를 기록하고 NAS 주소·경로·비밀번호·stream token을 기록하지 않는다.

2026-10-13 실제 실행 로그에서 Vulkan/Adreno 740·Linear 색공간·maxTexture 16384, XR display `running=True`, 양손 aim/pinch 바인딩 및 추적 활성, Room 저장소 조회 완료(114ms)를 확인했다. 이어서 우측 컨트롤러 바인딩과 UI hit, SMB 서버 저장·폴더 탐색·영상 선택, 1280×960/29.97fps·4096×2048/60fps·8192×4096/59.94fps 영상의 준비 및 첫 프레임을 확인했다. 사용자가 영상 재생을 확인했다. 소리·양안 품질·손 pinch 클릭은 별도 확인 전이다. 시작 중 AssetPackManager 클래스 검색 예외와 일부 GPU 포맷 시험 실패 로그가 있으나 이후 XR와 브리지는 실행됐다.

사용자가 seek 문제를 보고했다. 실제 8K 영상 로그에서는 완료 전에 여러 seek 요청이 쌓이고 복원 상태가 True에서 False로 바뀌었다. 수정은 native seek 한 개 + 최신 대기 위치 한 개로 제한하고, 재생 의도와 실제 isPlaying을 구분한다. `seekCompleted`는 디코딩 완료 신호이고 `time`은 새 프레임이 표시되어야 갱신되므로, `frameReady`까지 기다린 뒤 다음 seek와 재생/일시정지를 처리한다. 탐색 중에는 음소거한 채 새 프레임을 표시하고 사용자가 원한 상태로 복원한다. [Unity VideoPlayer.time 공식 설명](https://docs.unity.com/en-us/engine/6000.5/script-reference/unityengine/video/videoplayer/time)

연속 seek·최종 목적지·일시정지·이전 프레임 무시·영상 변경의 상태 검증 9개, 브리지 테스트 3개, C# 컴파일 오류/경고 0을 통과했다. HTTP range의 offset/length·reader 준비·read/write 시간·실제 전송량을 추가 기록한다. 청크 또는 SMB 병목 여부는 새 APK 재검증 전까지 확정하지 않는다.

seek 수정 빌드 `build_8389b36f69d9`는 성공했다(70.9초, 오류 0, 경고 8). 같은 `xr-stream-unity-device.apk`를 34/37로 업데이트 설치·실행했고 같은 8K 영상의 seek 재검증을 요청했다. 아래 기록처럼 사용자가 정상 동작을 확인했다.

헤드셋에서 메뉴가 안 보였던 실행은 XR 세션이 SYNCHRONIZED에 머물렀다. 사용자가 헤드셋을 재시작한 뒤 같은 seek APK를 재설치했을 때 VISIBLE/FOCUSED 진입과 메뉴/영상 선택이 확인됐다. 이어보기 42.44초의 HTTP offset 이동, 8K seek의 실제 프레임 완료도 기록했다. 사용자가 **seek·next/prev 파일 재생 정상**을 확인했다. 이 로그에서 HTTP 작업 큐는 0이었고, range reader 준비는 약 77–88ms였다. 일부 8K seek는 GOP 디코딩·추가 range 요청과 함께 0.29–4.08초 걸렸다.

외부 자막·오디오 선택 빌드 `build_6e80f8ef5327` 성공(78.9초, 오류 0, 경고 9). 기존 Media3 자막 파서와 ASS 색상 복원 코드를 브리지에서 재사용하며 ExoPlayer 재생 세션은 만들지 않는다. 자막 입력은 4 MiB·20,000 구간으로 제한한다. 한국어 자막용 Noto Sans KR은 [공식 배포](https://github.com/notofonts/noto-cjk/blob/main/Sans/SubsetOTF/KR/NotoSansKR-Regular.otf)를 사용하고 `Resources/Fonts/OFL.txt`에 라이선스를 포함했다.

자막 시간 경계·역방향 seek·빈 자막·한국어 글리프 검증 7개와 기존 seek 상태 9개, 브리지 테스트 3개, Unity C# 오류/경고 0을 통과했다. 새 APK 설치·실행과 테스트 파일 전송을 완료했다. 후속 설정 APK 설치 뒤 사용자가 전체적인 영상 재생·자막 정상 동작을 확인했다. ASS 색상·오디오 전환·설정 영속성의 개별 확인은 별도 기록이 필요하다.

후속 기능 수정에서는 Display의 `Subtitle settings`에 Small/Medium/Large/ExtraLarge, 거리 0.7–2.0m(0.1m 단위), 상하·UI 좌우 위치 ±0.3m를 연결했다. 원본 RuntimeConfig의 범위와 기본값, SharedPreferences의 키를 재사용한다. 거리 변경 시 물리적 크기를 같이 조절하여 각도 크기를 유지하고 설정은 조절 종료·메뉴 닫기·앱 pause/quit 시 저장한다. Recenter는 원본처럼 UI 좌우 위치를 0으로 되돌린다. 별도 앱 ID이므로 원본 앱의 저장값이 자동 이전되지는 않는다.

원본의 95% 기준을 누락했던 이어보기를 수정하고 마지막 영상 종료 후 Play로 재생을 시작하게 했다. HTTP 연결 종료 검증의 Connection reset은 응답 FIN 전송 후 peer 종료를 제한 시간 내 수신하도록 수정했다. 해제된 파일의 404 응답 20회 반복을 포함한 브리지 테스트 6개, 자막 설정·타이밍·폰트 검증 10개와 seek 검증 9개가 통과했다. Editor에서 설정 패널 생성·슬라이더 5개·전체 메뉴의 한국어 글리프도 확인했다. 기능 검증용 화면 외형은 그대로 두고 최종 UI 적용은 마지막에 진행한다.

설정 APK `build_7988bb28a9a0`는 69.9초, 오류 0·경고 8로 빌드하고 설치까지만 수행했다. 다음 단계로 컨트롤 자동 숨김·중첩 조작 잠금·300ms 입력 억제를 적용했다. 타임라인·음량·설정 슬라이더 조작 중에는 컨트롤을 숨기지 않으며 앱 pause 시 진행 중 조작을 취소한다. 빈 공간의 trigger/pinch 입력으로 컨트롤을 다시 표시하고, 해당 입력은 버튼 실행에 전달하지 않는다.

seek 미리보기는 타임라인 위 별도 카드이며 공통 2단 바의 위치·버튼은 유지한다. 본편을 기존 위치에서 일시정지한 뒤 별도 Unity VideoPlayer가 최대 960×270 RenderTexture로 출력한다. CPU 프레임 읽기·전체 파일 다운로드는 수행하지 않는다. 미리보기는 기존 2초 bucket 기준을 사용하되 실제 seek 위치는 바꾸지 않고, native seek 한 개·최신 대기 대상 한 개로 제한한다. drag 확정·취소·pause·영상 변경 시 디코더와 GPU texture를 해제한다. 오류는 미리보기 카드와 로그에 표시하며 본편 seek 조작은 유지한다. 미리보기 캐시는 아직 이전하지 않았다. 컨트롤 상태 검증 12개·미리보기 target 검증 6개·자막 검증 10개·seek 검증 9개·C# 오류/경고 0을 통과했고 실제 8K의 동시 디코더·프레임 시각 품질은 착용자 확인 전이다.

최종 APK `build_b928b79c9c46`는 47.7초·오류 0·경고 8로 빌드하고 설치만 했다. 재생 오류나 앱 pause 뒤에는 숨겨진 컨트롤을 다시 표시한다. 사용자는 seek preview가 표시되는 것을 확인했지만 양안이 합쳐지지 않는 문제를 보고했다. 일반 RawImage 출력에 눈별 UV 분할이 없어 생기는 미구현 항목이며, 사용자 중단 요청으로 수정은 다음 작업에 남겼다.

## 자막·멀티 오디오 테스트 파일

설치된 FFmpeg로 `.\tools\Create-UnityPlaybackFixtures.ps1`을 실행하면 24초 H.264 영상에 440Hz(kor)·880Hz(eng) AAC 트랙 두 개를 넣고 한국어 SRT/ASS sidecar를 만든다. FFmpeg 설치는 수행하지 않는다. 이 파일은 기능 시험용이며 실제 8K 본편 검증을 대체하지 않는다.

현재 기기의 `Movies/XRStreamTest`에 파일 4개를 전송했다. `Local folder`로 해당 폴더를 선택한다.

- `XRStream_AudioSrt.mp4`: 한글 SRT·seek 후 자막 시간·Display의 Audio 1/2 전환과 낮은/높은 소리.
- `XRStream_Ass.mp4`: 한글 ASS·노랑/빨강 색상·Display의 자막 On/Off.
- 두 영상 모두 Display → Subtitle settings에서 크기·거리·위치 조절 후 앱을 직접 닫고 다시 열어 설정 유지와 완료 영상의 처음부터 재생을 확인한다.

기기를 USB 디버깅으로 연결하고 디버깅 허용을 누른다. 업데이트는 **APK 설치까지만** 수행한다. 사용자의 별도 실행 요청이 없으면 Codex는 `adb am start`, `monkey`, Unity Build And Run 등으로 앱을 실행하지 않는다. 헤드셋이 깨어 있는 상태에서 사용자가 앱 목록에서 직접 실행한다. 사용자는 sleep 상태에서 앱이 실행되면 화면이 보이지 않고 앱 목록/OS 화면 전환도 막히는 현상을 보고했으며, 원인은 확정하지 않았다. SDK 값은 먼저 `adb shell getprop ro.build.version.sdk`로 확인해 APK의 Minimum 이상이어야 한다.

```powershell
$adb = 'D:\util\unityHub\editor\6000.6.5f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK\platform-tools\adb.exe'
& $adb devices -l
& $adb shell getprop ro.build.version.sdk
& $adb install -r 'G:\workspace\xr-stream-app\build\unity-port\xr-stream-unity-device.apk'
```

테스트 후 실행 중인 앱의 로그와 기기/API/앱 설정, 이전 프로세스 종료 원인을 수집한다. 앱을 설치하거나 실행하거나 로그를 지우지 않는 수집 도구다. 앱이 종료됐다면 `exit-info.txt`를 확인하고, 다시 실행한 뒤 수집해 현재 PID의 `logcat.txt`를 얻는다. 기기가 여러 개면 `-Serial`을 지정한다.

```powershell
.\tools\Collect-UnityDeviceDiagnostics.ps1
```

결과는 `build/unity-port/device/<시각>/`에 저장된다. 앱이 종료된 경우 마지막 종료 PID의 로그도 수집하며 `device.json`에 실행 중인지 기록한다. 실제 영상이 양쪽 눈에 보이는지, 방향·색·오디오가 맞는지는 착용자가 확인해야 한다. 첫 프레임 로그만으로 시각 품질을 통과 처리하지 않는다.

## Galaxy XR에서 확인할 순서

1. 검증용 APK를 설치한다. 헤드셋을 깨운 뒤 사용자가 직접 앱을 실행하고 손/컨트롤러로 버튼 클릭, 목록 스크롤과 타임라인 drag를 확인한다.
2. `Local folder`로 실제 영상 폴더를 선택한다. 새 앱 ID이므로 원본 앱의 권한·DB가 자동 이전되지 않는다.
3. `Add SMB server`로 실제 NAS를 추가한다. share를 비워 두면 서버 share 목록을 탐색한다. 인증 접속과 게스트 접속은 각각 확인한다.
4. 실제 2D·VR180·VR360 및 Mono·SBS·TB 영상을 재생한다. `Display`에서 형식과 스테레오를 지정하고 좌우 눈·방향·색·seam을 확인한다.
5. 고해상도/대용량 파일에서 ±10초, 타임라인 seek, 재생 복원, 이전/다음, 음량·배속을 확인한다.
   타임라인 drag 중에는 위쪽 미리보기와 target 시간이 보이고, 놓으면 해당 위치에서 원래 재생/일시정지 상태로 돌아와야 한다. 10초 자동 숨김 후 빈 공간 trigger/pinch로 재표시하고, 재표시 입력으로 버튼이 실행되지 않는지도 확인한다.
6. `Exit` 후 재진입, 앱 pause/resume, 네트워크 끊김과 `Recenter`의 3초 카운트·배치를 확인한다.

문제가 있으면 영상의 codec·해상도·형식, 로컬/SMB 구분, 수행한 조작과 오류 문구를 기록한다. 출력 후보를 기기에서 확인하기 전에는 P2 완료나 재생 엔진 확정으로 처리하지 않는다.

기기 검증은 시작/입력 → 로컬 2D → 같은 영상의 SMB → VR180/360·SBS/TB → 고해상도·seek·이어보기 → 앱 pause/resume·네트워크 끊김 순서로 진행한다. 앞 단계가 실패하면 해당 로그와 착용자가 본 증상부터 해결한다. 자막·오디오 트랙 선택과 나머지 기능 이전은 첫 출력 경로의 실기기 결과를 받은 뒤 진행하며 UI 완성도 작업은 마지막으로 둔다.

## 브리지 수명

- IO 요청 결과는 JSON event로 전달하고 Unity main thread에서 poll한다.
- 탐색 generation 변경 시 대기 요청을 취소한다. 늦게 도착한 영상 응답의 stream token도 해제한다.
- 각 HTTP GET은 독립 SAF descriptor 또는 SMB random-access reader를 소유하고 닫는다.
- 영상 종료/변경/씬 종료 시 위치 저장 요청 → 디코더 정지·RenderTexture 해제 → stream token/socket 해제 순서로 처리한다.
- cleartext HTTP 허용 대상은 `127.0.0.1`이다. SMB 비밀번호는 Unity 목록 응답이나 재생 URL에 넣지 않는다.
