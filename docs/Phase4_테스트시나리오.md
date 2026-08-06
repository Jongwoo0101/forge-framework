# ForgeFramework Phase 4 테스트 시나리오

## 0. 실행 방법

```bash
./scripts/build.sh
./scripts/run.sh < tests/phase4_scenario_input.txt
```

`tests/phase4_scenario_input.txt`는 아래 모든 시나리오를 **하나의 연속된 세션**으로 이어붙인 스크립트입니다.
Kernel/FileSystemManager는 프로세스 하나가 뜨는 동안 상태를 계속 유지하므로, TC 순서를 바꾸면 결과도 달라질 수 있습니다 (특히 4장 자원 고갈 테스트는 앞의 TC에서 소모된 inode 개수에 의존).

각 TC는 `현재 빌드에서 실제로 실행해 검증한 결과`를 "기대 결과"로 기록했습니다.

---

## 1. 기본 CRUD 흐름

| TC | 명령어 | 기대 결과 |
|----|--------|-----------|
| TC-01 | `pwd` | `/` (부팅 직후 CWD는 루트) |
| TC-02 | `ls` | 빈 디렉터리이므로 헤더(`NAME | TYPE | SIZE`)만 출력, 엔트리 없음 |
| TC-03 | `mkdir usr` | `디렉터리가 생성되었습니다: usr` |
| TC-04 | `ls` | `usr \| DIRECTORY \| 0` 1건 표시 |
| TC-05 | `mkdir usr/local` | `디렉터리가 생성되었습니다: local` (중첩 경로 바로 생성) |
| TC-06 | `mkdir usr/local` (재실행) | `이미 존재하는 이름입니다: local` |
| TC-07 | `cd usr/local` → `pwd` | `/usr/local` |
| TC-08 | `touch readme.txt` | `파일이 생성되었습니다: readme.txt` (size 0) |
| TC-09 | `touch readme.txt` (재실행) | 에러 없이 `파일이 생성되었습니다: readme.txt` 재출력 — touch는 유닉스처럼 **idempotent** |
| TC-10 | `ls` | `readme.txt \| FILE \| 0` |
| TC-11 | `write readme.txt Hello ForgeFramework Phase4` | `27바이트 기록됨` |
| TC-12 | `cat readme.txt` | `Hello ForgeFramework Phase4` |
| TC-13 | `write readme.txt Overwritten content` | `19바이트 기록됨` (append가 아니라 **덮어쓰기**임을 확인) |
| TC-14 | `cat readme.txt` | `Overwritten content` (이전 내용이 남지 않음) |

## 2. 경로 해석 (절대/상대/`.`/`..`)

| TC | 명령어 | 기대 결과 |
|----|--------|-----------|
| TC-15 | (TC-07 상태에서) `cd ..` → `cd ..` → `pwd` | `/` — 상위로 2단계 이동 |
| TC-16 | `tree` | 루트부터 `usr/ → local/ → readme.txt` 트리 구조 출력 |
| TC-17 | `cd /usr/local` → `pwd` | `/usr/local` — 절대경로 이동 |
| TC-18 | `cd .` → `pwd` | `/usr/local` — 제자리 유지 |
| TC-19 | `cd ../..` → `pwd` | `/` — 상대경로 조합(`../..`) 정상 처리 |
| TC-20 | `cd nowhere` | `경로를 찾을 수 없습니다: nowhere`, **CWD는 변경되지 않음** (Kernel이 실패 시 절대경로를 반환하지 않으므로 Shell의 CWD도 그대로 유지) |

## 3. 예외/경계 처리

| TC | 명령어 | 기대 결과 |
|----|--------|-----------|
| TC-21 | `mkdir empty_dir` → `rm empty_dir` | 생성 후 즉시 삭제 성공 (빈 디렉터리는 삭제 가능) |
| TC-22 | `mkdir usr/local/sub` → `touch usr/local/sub/inner.txt` → `rm usr/local` | `비어있지 않은 디렉터리는 삭제할 수 없습니다: usr/local` |
| TC-23 | `rm usr/local/sub/inner.txt` → `rm usr/local/sub` → `rm usr/local` | 자식부터 순서대로 지우면 삭제 가능 → 그래도 `usr/local`엔 `readme.txt`가 남아있어 마지막 `rm usr/local`은 다시 `비어있지 않은 디렉터리는 삭제할 수 없습니다` |
| TC-24 | `cat usr` (디렉터리 대상) | `디렉터리는 cat으로 읽을 수 없습니다: usr` |
| TC-25 | `write usr hack` (디렉터리 대상) | `디렉터리에는 쓸 수 없습니다: usr` |
| TC-26 | `touch usr` (이미 디렉터리인 이름) | `이미 디렉터리로 존재합니다: usr` |
| TC-27 | `rm doesnotexist` | `존재하지 않는 경로입니다: doesnotexist` |
| TC-28 | `cat doesnotexist` | `경로를 찾을 수 없습니다: doesnotexist` |

## 4. 자원 한계 (Bitmap 고갈)

> 기본 설정: `TOTAL_BLOCKS=16`, `BLOCK_SIZE=16B`(총 256B), `TOTAL_INODES=16`.
> 아래 TC는 1~3장에서 이미 소모된 inode(`root, usr, local, readme.txt` = 4개)가 남아있는 상태에서 이어집니다.

| TC | 명령어 | 기대 결과 |
|----|--------|-----------|
| TC-29 | `touch bigfile.txt` → `write bigfile.txt <133바이트 문자열>` | `133바이트 기록됨` — 남은 12블록(192B) 이내라 정상 처리 |
| TC-30 | `mkdir d1` ~ `mkdir d11` (11개 연속 생성) | 전부 성공 → 이 시점에서 inode 사용량이 `4(기존) + 1(bigfile) + 11(d1~d11) = 16` 으로 **정확히 꽉 참** |
| TC-31 | `mkdir d12` | `inode가 부족합니다.` |
| TC-32 | `mkdir d13` | `inode가 부족합니다.` (반복 시도해도 계속 실패, 시스템이 죽지 않고 정상 응답) |
| TC-33 | `tree /` | d12/d13은 생성되지 않았으므로 트리에 나타나지 않고 `usr/local/readme.txt`, `bigfile.txt`, `d1~d11`만 표시 |

### (참고) 디스크 블록 고갈은 별도로 확인하려면

`TOTAL_BLOCKS`를 임시로 줄이거나(`ForgeOSConstants.TOTAL_BLOCKS`), 위 TC-29에서 `BLOCK_SIZE × TOTAL_BLOCKS`(현재 256B)를 초과하는 문자열을 `write`하면 됩니다. 이 경우 `FileSystemManager.write()`의 롤백 로직에 따라 **새 블록 확보 실패 시 이미 확보했던 새 블록만 반납되고 기존 파일 내용은 보존**되는지까지 확인하는 것이 핵심입니다 (교체 전 내용 유지 여부).

---

## 5. Kernel 무상태성(Stateless) 확인 포인트

파일 시스템 자체 기능은 아니지만, 명세서 3항의 핵심 설계 요구사항이므로 코드 리뷰 관점에서 함께 체크합니다.

- [x] `FileSystemManager`의 모든 public 메서드가 `cwd`를 인자로 받고, 내부에 CWD를 저장하는 필드가 없는지 확인 (`FileSystemManager.java` 확인 결과 CWD 필드 없음 — 통과)
- [x] CWD 상태는 오직 `ShellContext`에만 존재하며, `cd` 성공 시에만 `CdCommand`가 갱신하는지 확인 (TC-20에서 실패 시 CWD 유지되는 것으로 간접 검증됨)
- [x] 프롬프트(`forgeframework:/usr/local> `)가 매 명령마다 `ShellContext.getCwd()`를 다시 읽어 렌더링하는지 확인 (`ShellPrompt.render()` 확인 결과 통과)

---

## 6. 회귀 테스트 자동 재실행

TC-01 ~ TC-33을 그대로 이어붙인 `tests/phase4_scenario_input.txt`를 실행하면, 이번에 실제로 캡처해 검증한 원본 로그가 `tests/phase4_scenario_raw_output.txt`에 저장되어 있습니다. 코드를 수정한 뒤 같은 스크립트를 다시 돌려 두 출력(diff)이 여전히 같은 흐름을 유지하는지 비교하는 용도로 쓰시면 됩니다.

```bash
./scripts/run.sh < tests/phase4_scenario_input.txt > tests/latest_run.txt
diff tests/phase4_scenario_raw_output.txt tests/latest_run.txt
```

(타임스탬프 로그 라인만 매번 달라지므로, 완전히 자동화하려면 diff 전에 `[HH:MM:SS.mmm]` 부분을 정규식으로 제거하는 전처리가 필요합니다.)
