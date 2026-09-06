# XR 재생·최근 항목 개선 — 2026-09-06

## 적용 내용

- Activity 메인 패널은 `DashboardPanelHost` 한 곳에서 소유한다. 재생 중에도 크기·pose를 보존하고, 파일 목록 레이아웃 완료 후 표시한다. 기존 2×2dp 축소와 4000dp 화면 밖 이동은 제거했다. 재생 진입·복귀의 NavHost fade도 제거했다.
- 재생 컨트롤·자막·미리보기·메뉴는 유지되는 SceneCore 부모 엔티티로 표시와 hit testing을 함께 제어한다. 자막의 거리·양안 보정·vertical 설정은 변경하지 않았다. 컨트롤 표시 중 자막을 숨기는 기존 표시 정책은 유지한다.
- 배경 입력면은 머리를 따라간다. 고정된 컨트롤·메뉴·미리보기를 포함하는 거리 경계보다 뒤에 배치하고, 패널 surface 해상도를 늘리는 대신 공간 scale로 각도 범위를 유지한다. 실제 기기 hit 순서 검증은 남아 있다.
- 숨겨진 컨트롤은 키·스크롤 처리와 포커스 요청도 중단한다. 지연된 공간 클릭은 활성화 세대를 검사한다. `PlaybackLayerDebug` 로그에서 패널 활성 상태와 배경·자막·컨트롤·메뉴의 입력 수신을 구분할 수 있다.
- re-center는 현재 XR 프레임의 머리 위치와 pitch/yaw를 한 번 캡처한다. roll은 제거하고 수직 시야에서는 이전 수평 방향을 보존한다. 180/360 영상·컨트롤·자막이 동일한 중심을 사용하며 연속 영상 follow는 없다. 추적 중단 또는 250ms 초과 샘플에서는 요청을 거부한다.
- OS 원점 변경 후에는 새 프레임을 기다린다. 재생 re-center는 대시보드 배치를 초기화하지 않는다. 기존 줌·영상 각도·공통 가로 보정 초기화는 유지한다.
- 폴더 로딩 중 기존 목록을 유지하며 실행 버튼은 로딩 완료 전 동작하지 않는다. 대시보드 내부 전환에서는 지연 fade와 기본 크기 보간을 제거했다. 재생 초기화 오류에는 돌아가기 버튼을 제공한다.

## NFO와 썸네일

- NFO 파일 읽기는 폴더 목록 로딩 성공 후 시작되는 작업에만 있다. 폴더 재진입·명시적 목록 새로고침은 갱신하며, 카드 표시·최근 화면 진입·재생 복귀·정렬·백그라운드 라이브러리 스캔은 NFO를 읽지 않는다.
- 폴더 작업 안에서 동일 디렉터리 목록과 NFO 텍스트를 재사용한다. source와 전체 파일 경로로 캐시를 구분하므로 작품 코드가 없는 파일도 지원한다. 메타데이터 본문은 기존 Room 메타데이터 테이블을 재사용한다.
- 최근 카드는 DB Flow를 관찰한다. 실패·잘못된 XML은 기존 캐시를 유지한다. 정상 목록에서 NFO가 없으면 확인된 부재를 저장한다. 폴더 이동·연결 해제로 취소된 작업은 새 목록에 결과를 적용하지 않는다.
- 마지막 위치 프레임은 일반 작품 썸네일과 별도로 보관한다. 최근 카드는 생성 중 이전 로컬 이미지를 유지하며 영상 원본에서 프레임 추출을 요청하지 않는다.
- 일시정지·종료 때 위치를 저장한 뒤 프레임을 생성한다. 같은 프레임의 캐시를 재사용하고 생성 작업은 직렬화한다. 완료 시 DB의 `lastPosition`이 캡처한 값과 일치할 때만 공개한다. 다른 파트·즐겨찾기에 이어보기 프레임을 전파하지 않는다.
- 재생 중 주기적 프레임 추출은 추가하지 않았다. 새 이미지와 직전 이미지를 유지하고 이전 이어보기 프레임 파일을 정리한다.
- Room 16→17 마이그레이션은 `file_nfo_cache`, `resumeThumbnailPath`, `resumeThumbnailPositionMs`를 추가한다. 기존 기록과 설정을 삭제하지 않는다.

## Galaxy XR Grip+Trigger: 미연결

기존 CLI/앱 작업 기록에서 일반 key/joystick axis는 확인되지 않았고 Virtual 포인터와 Compose scroll 경로가 관찰됐다. 현재 설치된 XR Compose alpha17, Runtime/ARCore/SceneCore beta02 소스에도 Grip을 구분하는 공개 필드가 없다. SceneCore InputEvent는 source, pointer, ray, primary action을 제공한다.

- [SceneCore InputEvent](https://developer.android.com/reference/kotlin/androidx/xr/scenecore/InputEvent)
- [Entity.setEnabled: 렌더링·입력 비활성화](https://developer.android.com/reference/kotlin/androidx/xr/scenecore/Entity)

키코드 추측이나 다른 제스처로 대체하지 않았다. Recenter 버튼은 접근 수단으로 유지한다. Android motion 진단에는 trigger/brake/gas 축, buttonState, actionButton을 추가했다. 이는 매핑이나 동작 보장을 뜻하지 않는다.

다음 기기 검증에서는 같은 손의 Grip 단독, Trigger 단독, Grip+Trigger를 UI 표시/숨김 및 ray가 패널에 닿지 않는 상태에서 구분한다. 독립적인 press/release 수신이 확인되어야 조합을 연결하고 기존 버튼을 제거할 수 있다. 현재 SDK 경로로 구분되지 않는다면 별도의 원시 컨트롤러 action 지원 경로/SDK 검토가 필요하다.

## 검증

- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline`: 단위 테스트 152개 통과(실패·오류·건너뜀 0), Debug APK 빌드 성공.
- `python tools/verify_storage_migration.py`: 검증 3개 통과. 실제 Room 16 스키마에 실제 마이그레이션 SQL을 적용해 17 스키마와 비교. 기록 보존·NFO source 분리·오래된 썸네일 공개 차단 검증.
- `git diff --check`: 통과.
- 기존 Gradle 버전 변경은 보존했으며 Android SDK 설정은 변경하지 않았다.

최초 구현 단계에서는 실기기 설치·계측 테스트를 실행하지 않았다. 이후 사용자가 설치한 빌드의 ADB 로그를 아래와 같이 확인했다. 2D/180/360 반복 복귀 시 첫 창 크기, 머리를 돌린 상태의 컨트롤 hit, 자막 거리/vertical 범위, 메뉴·seek 반복 전환, 현재 시야 re-center 후 고정 여부는 Galaxy XR에서 확인해야 한다. 실제 intercept 해결 및 Grip+Trigger 지원은 검증 완료 상태가 아니다.

## 180도 오른쪽 컨트롤러 재현 후 보완

- 2026-09-06 11:03~11:04 사용자 버튼 테스트: SceneCore `CONTROLLER / RIGHT / DOWN·UP` 및 Compose 가로·세로 스크롤 확인. Android/Compose key 이벤트는 0건이며 Grip과 Trigger의 구분 정보는 확인되지 않았다. 입력 로그는 로컬 `.tmp/right-controller-180.log`에 보존했다.
- 이전 CLI 세션(01a00f8f-f881-7531-8fe1-7690537330a9)의 버튼 응시 방향을 캡처하면 재설정 때마다 UI가 이동한다는 지적을 재확인했다. 현재 시야 기준 기능을 UI 버튼에 즉시 연결한 것은 회귀였다.
- 사용자가 선택한 대로 UI는 `Recenter in 3s`로 변경했다. 클릭 시 pose를 저장하지 않으며, 3초 뒤 요청이 발생하면 최신 유효한 머리 pose를 읽는다. 중복 클릭은 차단하고 영상 변경·종료 시 대기 요청을 취소한다. Grip+Trigger는 연결하지 않았다.
- 사용자는 자막이 보이지 않는다고 보고했다. 로그에는 subtitle cue와 자막 레이어 활성화가 있었고 저장 설정은 거리 1m, vertical/horizontal 0이었다. 따라서 로그의 enabled만으로 표시 성공을 판단할 수 없다.
- 컨트롤 표시·숨김 때 Android SubtitleView를 제거하던 조건을 없앴다. 최초 준비 이후 재버퍼링 시 기존 자막·컨트롤 surface를 유지한다. 자막의 실제 레이아웃 크기·pose·거리 진단 로그를 추가했다. 거리·세로 설정은 변경하지 않았다.
- 수정 후 단위 테스트 152개 및 Debug APK 빌드가 통과했다. 수정 APK의 자막 표시 복구와 3초 재설정 동작은 아직 기기에서 검증하지 않았다.
