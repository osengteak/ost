# Central Economy - Miner v0.5 (Minecraft Java 26.2 / Fabric)

이 저장소는 **GitHub Actions에서 자동 빌드**하도록 준비되어 있습니다.
내 컴퓨터에 JDK, Gradle, IntelliJ를 설치할 필요가 없습니다.

## 목표 기능

- 조각된 석영 블록을 광부 작업대로 사용
- 성인 무직 주민이 광부로 자동 취직
- 광부가 되면 `[광부]` 표시
- 광부 전용 중앙시장 UI
- 플레이어별 A/B 국가매입 쿼터
- 모든 광부가 공유하는 국가판매 재고
- 7 Minecraft day 계획주기
- 검색 / 즐겨찾기 / 가격정렬 / 재고 표시
- 월드 저장 데이터 영속화

## GitHub에서 빌드하기

1. GitHub에서 새 repository를 만듭니다.
2. 이 폴더의 **내용 전체**를 repository에 업로드합니다. `.github` 폴더도 반드시 포함해야 합니다.
3. Commit하면 `Actions` 탭에서 **Build Miner Mod**가 자동으로 시작됩니다.
4. 초록색 체크가 뜨면 해당 실행을 열고 맨 아래 `Artifacts`에서 **central-economy-miner-jar**를 받습니다.
5. 받은 ZIP을 풀면 `central-economy-miner-0.5.0.jar`가 있습니다.
6. 기존 Minecraft 26.2 Fabric Essential 프로필의 `mods` 폴더에 그 JAR을 넣습니다. Fabric API도 그대로 유지합니다.

## 빌드가 실패하면

실패한 Actions 실행 맨 아래 `Artifacts`에서 **central-economy-miner-build-log**를 다운로드하세요.
그 ZIP 안의 `build-output.log`를 ChatGPT에 올리면 실제 Minecraft/Fabric 26.2 컴파일 오류를 기준으로 수정할 수 있습니다.

## 중요

이 소스는 경제 코어 설계를 포함하지만, **GitHub Actions의 실제 Fabric Loom 컴파일이 성공하기 전까지 설치 가능한 JAR로 검증되었다고 간주하지 않습니다.**
