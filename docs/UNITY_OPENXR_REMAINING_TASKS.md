# Unity/OpenXR 포팅 잔여 작업

기준일: 2026-10-13. 사용자 요청으로 오늘 작업을 중단한다.
전체 포팅과 기능 동등성 검증은 아직 완료하지 않았다.

## 작업 원칙

- 기능 구현·실기기 검증을 우선하고 최종 UI는 마지막에 적용한다.
- Jetpack/Unity 혼합 UI 제안은 의견이었다. 기존 Unity/OpenXR 포팅 방향과 최종 UI 기준을 유지한다.
- 업데이트 APK는 설치까지만 한다. 사용자가 별도로 실행을 요청하지 않으면 ADB나 Unity Build And Run으로 실행하지 않는다.
- 헤드셋이 깨어 있는 상태에서 사용자가 직접 앱을 실행한다. sleep 상태에서 실행하면 화면이 안 보이고 OS 화면 전환까지 막히는 현상이 보고됐다. 원인은 확정하지 않았다.
- SDK·Editor·도구 문제는 임의 설치·교체 등으로 우회하지 않고 원인과 사용자 조치를 알린다.
- 기존 계획에서 이번 포팅 범위 밖으로 정한 기능 개선안은 추가하지 않는다.

## 현재 기준점

| 항목 | 상태 |
| --- | --- |
| Android 소스·브리지·Unity 원본 소스 저장소 | `G:\workspace\xr-stream-app` |
| 별도 Unity 저장소·프로젝트 | `G:\workspace\unity-stream-app\unity-xr-stream` |
| Editor | Unity 6000.6.5f1, 설치된 Android 모듈 사용 |
| 앱 ID | `blackark.app.vr.unity`, 기존 앱과 병행 설치 |
| SDK | Minimum 34 / Target 37 / compile 37 |
| 기기 | Galaxy XR SM-I610, Android 14/API 34 |
| 마지막 APK | `build/unity-port/xr-stream-unity-device.apk` |
| 마지막 빌드 | `build_b928b79c9c46`, 성공, 47,694ms, 오류 0·경고 8 |
| 배포 | 업데이트 설치 완료. Codex는 앱을 실행하지 않음 |
| 다음 시작점 | **seek preview의 SBS/TB 양안 출력 수정** |

Unity Inspector의 Target 선택창은 표시 목록과 내부 API 변환 기준이 어긋나는 버그가 확인됐다. 재컴파일 후 공개 PlayerSettings API로 34/37을 저장하고 APK 메타데이터를 확인하는 기존 빌드 절차를 사용한다. SDK를 다시 설치할 문제로 취급하지 않는다.

## 구현·확인 현황

| 기능 | 구현 | 실기기 확인 |
| --- | --- | --- |
| Unity/OpenXR 실행·컨트롤러 메뉴 입력 | 구현 | 실행·메뉴 조작 확인 |
| SMB 서버 저장·탐색·스트리밍 | 구현 | 사용자 영상 재생 정상 확인 |
| SAF 로컬 폴더 선택·재생 | 구현 | 시험 파일 재생 로그 확인 |
| 2D/180/360·Mono/SBS/TB GPU 출력 | 구현 | 영상 재생 확인. 형식별 양안·방향·색상 검증은 추가 필요 |
| ±10초·타임라인 seek | 수정 완료 | 사용자 정상 확인 |
| 이전/다음 파일 재생 | 구현 | 사용자 정상 확인 |
| 외부 SRT/ASS·한국어 폰트 | 구현 | 사용자 전체적인 자막 정상 확인 |
| 멀티 오디오 선택 | 구현 | 두 트랙 인식·선택 로그 확인. 실제 소리 전환의 개별 확인 필요 |
| 자막 크기·거리·상하 위치·UI 좌우 위치·저장 | 구현 | 조절·재실행 후 유지의 개별 확인 필요 |
| 95% 완료 영상 재진입·마지막 영상 Play 재시작 | 구현 | 개별 확인 필요 |
| 10초 자동 숨김·trigger/pinch 재표시·조작 잠금 | 구현 | 개별 확인 필요 |
| pause 중 drag 취소·오류/pause 뒤 컨트롤 재표시 | 구현 | 개별 확인 필요 |
| GPU seek preview | 구현 | 사용자가 표시 동작 확인. **양안이 합쳐지지 않는 문제 확인** |

자동 검증: 브리지 단위 테스트 6개, seek 상태 9개, 자막·설정·글리프 10개, 컨트롤 상태 12개, preview target 6개 통과. Unity C# 컴파일 오류·경고 0. 설정 패널 생성·슬라이더 5개·메뉴 한국어 글리프 확인. `git diff --check` 통과.

## 1. 다음에 먼저 수정할 문제

- [ ] **seek preview 양안 출력**
  - 현재 `XRStreamApp.CreatePreviewCard / PreviewReady`가 전체 SBS/TB RenderTexture를 일반 `RawImage`에 표시한다.
  - 현재 코드에는 preview의 눈별 UV 분할이 없다. 원인은 확인했으며 **수정은 아직 적용하지 않았다**.
  - 왼눈·오른눈에 각각 대응 영역을 전달하는 stereo shader 경로를 적용한다.
  - 카드 비율도 한쪽 눈 기준으로 맞춘다. SBS는 원본 폭의 절반, TB는 원본 높이의 절반을 사용한다.
  - Mono는 원래 비율을 유지한다. 2D·180·360, SBS·TB에서 실제 양안 결합과 눈 순서를 확인한다.
  - 본편 재생·seek·자막과 drag 확정/취소의 재생 복원이 유지돼야 한다.

관련 소스: `unity-port/Assets/XRStream/Runtime/XRStreamApp.cs`, `SeekPreviewPlayer.cs`, `Resources/StereoVideo.shader`. 실제 프로젝트에도 같은 Runtime 소스가 반영돼 있다.

## 2. 현재 구현의 실기기 검증 마무리

- [ ] `Movies/XRStreamTest/XRStream_AudioSrt.mp4`에서 Audio 1/2의 440Hz/880Hz 소리 전환 확인.
- [ ] `XRStream_Ass.mp4`의 노랑·빨강 ASS 색상과 자막 On/Off를 개별 확인.
- [ ] 자막 4단계 크기·0.7–2.0m 거리·상하/UI 좌우 위치 조절, 앱 종료·수동 재실행 뒤 유지 확인.
- [ ] 완료 영상 재진입 시 처음부터 재생, 마지막 영상의 Play 재시작 확인.
- [ ] 10초 자동 숨김, 빈 공간 trigger/pinch 재표시, 재표시 입력으로 버튼이 실행되지 않는지 확인.
- [ ] 타임라인·음량·설정 slider drag 중 자동 숨김/중복 입력 차단 확인.
- [ ] 재생/일시정지 상태에서 drag 확정·취소·앱 pause/resume 확인.
- [ ] 8K SMB 영상의 preview 연속 이동·취소·영상 변경, 동시 디코더 자원·프레임 품질 확인.
- [ ] 2D·VR180·VR360, Mono·SBS·TB의 눈 순서·방향·색상·seam 확인.
- [ ] 손 pinch·컨트롤러 조작, 네트워크 끊김·복구, 재생 오류 후 Exit/파일 재선택 확인.

필요 시 `tools/Collect-UnityDeviceDiagnostics.ps1`로 로그를 읽는다. 해당 도구는 앱을 설치·실행하거나 로그를 지우지 않는다. 착용자 확인 없이 시각·오디오 품질을 통과 처리하지 않는다.

## 3. 재생 기능 잔여 포팅

- [ ] 내장 자막 트랙 탐색·언어 표시·선택과 Off, 외부 자막과의 선택 정책.
- [ ] 원본 시스템 자막 글꼴 목록·선택·설정 저장. 현재는 Noto Sans KR 사용.
- [ ] 원본 FOV/zoom 동작과 범위 연결·실기기 검증.
- [ ] 플레이어 UI head-follow 설정 및 원본 모드별 배치·Recenter 정책 대조.
- [ ] seek preview 캐시·요청 간격·원본의 stereo 처리와 상태 정책 대조.
- [ ] 원본 배속 선택 범위 등 검증용 컨트롤의 빠진 항목 대조.
- [ ] codec·HDR/색공간·8K 성능·버퍼링·네트워크 오류 및 재생 엔진 지원 제한 정리.

## 4. 탐색·데이터·나머지 화면

- [ ] 홈 이어보기·최근 항목·재개 위치·즐겨찾기/빠른 접근 등 원본 도달 기능 대조·이전.
- [ ] SMB/로컬 탐색의 원본 정렬·그룹·펼친 폴더·혼재 폴더 정책.
- [ ] NFO·그룹·상세·제목·포스터·fanart·대표 이미지·썸네일.
- [ ] 라이브러리/SMB hover 및 trailer 미리보기, 이어보기 썸네일.
- [ ] 데이터 저장·자격 증명·삭제·초기화·기존 설정 항목 대조와 이전.
- [ ] 설정 화면·UI 언어·테마 등 원본 기능.
- [ ] YouTube/WebView 기능의 출력·입력·쿠키·전체 화면 및 원본 복귀 동작 검증·이전.

## 5. 최종 UI — 기능 검증 이후

- [ ] 기존 최종 목업/계획 기준으로 홈·탐색·상세·설정 화면 적용.
- [ ] 공통 넓은 2단 재생 바 유지. 메뉴/preview가 버튼과 타임라인을 가리지 않게 배치.
- [ ] XR 가독성·ray/pinch 조작·스크롤·키보드·접근성 검증.
- [ ] 전체 기능 목록과 도달 경로를 대조하고 APK·권한·수명·실기기 회귀를 마무리한다.

기준 문서: [UNITY_OPENXR_PORT_PLAN.md](UNITY_OPENXR_PORT_PLAN.md).
빌드·동기화·테스트 절차: [unity-port/README.md](../unity-port/README.md).

## 저장소 경계와 다음 재개 절차

1. Android 저장소의 `unity-port`를 소스 기준으로 수정한다.
2. Android 브리지 변경 시 `:unity-bridge:testDebugUnitTest :unity-bridge:exportUnityRuntime`을 실행한다.
3. `tools/Sync-UnityPort.ps1`로 별도 Unity 저장소에 반영한다.
4. 실행 중인 Editor의 live CLI에서 재컴파일·상태 검증·34/37 저장·APK 빌드를 수행한다.
5. APK 메타데이터 확인 후 **설치만** 한다. 사용자가 직접 실행해 결과를 확인한다.
6. 양안 preview 수정부터 검증한 뒤 나머지 기능 포팅을 이어간다.

Unity 저장소의 기존 템플릿 바이너리/LFS 정규화 변경, HubForceResolve 삭제 등 포팅과 관련이 확인되지 않은 변경은 이 포팅 커밋에 포함하지 않는다. 원래 변경을 되돌리거나 LFS/SDK 환경을 재설정하지 않는다.
