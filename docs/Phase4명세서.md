# ForgeFramework Phase 4 개발 명세서: File System

## 1. 개요 및 목표

운영체제의 핵심 서브시스템 중 하나인 파일 시스템을 가상으로 구현합니다. 실제 디스크를 제어하지는 않지만, 메모리 상에 가상 디스크 공간(Virtual Disk)을 만들고 실제 UNIX/Linux 기반의 **Inode 방식 파일 시스템**을 객체지향적으로 시뮬레이션합니다.

## 2. 아키텍처 및 핵심 컴포넌트 설계 (`filesystem` 패키지)

### 2.1. 하드웨어 가상화 계층

* **`VirtualDisk` (가상 디스크)**
* 고정된 크기의 Data Block 배열로 구성됩니다. (예: 1블록 = 1KB, 총 1024블록 = 1MB 가상 디스크)
* 실제 `byte[]`를 다루어 `write`/`cat` 시 문자열을 바이트로 변환해 저장합니다.


* **`SuperBlock` (슈퍼 블록)**
* 파일 시스템의 메타데이터를 저장합니다. (총 블록 수, 블록 크기, Inode 총 개수, 빈 블록 수, 루트(`/`) Inode 번호 등)


* **`Bitmap` (할당 장부)**
* `InodeBitmap`과 `DataBlockBitmap` 두 가지를 운용합니다.
* 어떤 Inode 번호와 Data Block 번호가 비어있는지 O(N) 또는 O(1)로 빠르게 찾고 할당/해제합니다.



### 2.2. 파일 시스템 구조 계층

* **`InodeType` (Enum)**
* `FILE` (일반 파일) / `DIRECTORY` (디렉터리) 구분.


* **`Inode` (인덱스 노드)**
* 파일이나 디렉터리의 고유 메타데이터 객체입니다.
* 필드: `inodeNumber`, `type`(FILE/DIR), `size`(바이트 크기), `directBlocks`(할당된 데이터 블록 번호 배열).
* *참고:* 파일 이름은 Inode에 저장되지 않습니다.


* **`Directory` (디렉터리 엔트리 관리자)**
* 실제 OS처럼 디렉터리도 하나의 파일(Inode)로 취급되나, 시뮬레이션의 편의성을 위해 Inode가 이 객체를 래핑하여 갖게 합니다.
* 내부 구조: `Map<String, Integer> entries` (파일/디렉터리 이름 ➔ Inode 번호 매핑).



### 2.3. 매니저 계층

* **`FileSystemManager` (Facade)**
* 커널에 주입되는 파일 시스템 총괄 매니저.
* **경로 해석(Path Resolution):** `/a/b/c` 같은 경로가 들어오면 루트(`/`)부터 시작해 Inode를 순차적으로 찾아내는 로직을 전담합니다.
* 디스크 포맷(초기화), 파일/디렉터리 생성, 삭제, 읽기, 쓰기 로직을 수행합니다.



---

## 3. 상태 관리와 API 중심 설계 (매우 중요)

나중에 웹 프론트엔드(React 등)와 연동하려면 백엔드(Kernel)는 무상태(Stateless)를 유지해야 합니다. 따라서 **현재 작업 디렉터리(Current Working Directory, CWD)의 상태는 커널이 아니라 클라이언트(Shell)가 가집니다.**

1. **Shell 계층 (`ForgeShell` & `ShellPrompt`):**
* 내부에 `String currentWorkingDirectory = "/";` 상태를 가집니다.
* 프롬프트를 `forgeframework:/usr/local> `처럼 CWD를 반영하여 렌더링합니다.
* 시스템 콜을 보낼 때 **항상 CWD 경로를 인자(args)로 같이 보냅니다.**


2. **Kernel / FileSystemManager 계층:**
* 전달받은 CWD와 입력된 경로(Target Path)를 조합하여 절대 경로로 해석한 뒤 로직을 처리합니다.
* 성공 여부와 **순수 데이터(DTO)만 반환**합니다.



---

## 4. DTO 설계 (Data Transfer Object)

Kernel 계층이 Command 계층으로 반환할 순수 데이터 구조체들입니다. (`record` 활용 권장)

* `DirectoryEntryDto(String name, String type, int size)`: `ls` 명령 시 각 파일/폴더의 정보.
* `FileListDto(String currentPath, List<DirectoryEntryDto> entries)`: `ls` 명령의 최종 반환 객체.
* `FileContentDto(String name, String content)`: `cat` 명령의 반환 객체.
* `TreeNodeDto(String name, boolean isDirectory, List<TreeNodeDto> children)`: `tree` 명령용 재귀적 트리 구조.

---

## 5. 시스템 콜 및 명령어(Command) 명세

모든 명령어는 `SystemCallType`에 추가되며, `Command` 계층에서 포맷팅을 담당합니다.

| 명령어 | 입력 형식 (Shell) | SystemCall 내부 동작 | 반환 DTO | 기능 설명 |
| --- | --- | --- | --- | --- |
| **`pwd`** | `pwd` | (Kernel 호출 없이 Shell 자체 상태 출력) | - | 현재 작업 디렉터리 절대 경로 출력 |
| **`cd`** | `cd <path>` | 유효한 디렉터리인지 확인 (`CD`) | `String` (변경된 절대경로) | 경로 이동. 성공 시 Shell의 CWD 상태 업데이트 |
| **`ls`** | `ls [path]` | 경로 내 엔트리 목록 조회 (`LS`) | `FileListDto` | CWD 또는 지정된 경로의 파일/폴더 목록 출력 |
| **`mkdir`** | `mkdir <name>` | 빈 Directory Inode 생성 및 매핑 (`MKDIR`) | `DirectoryEntryDto` | 새로운 디렉터리 생성 |
| **`touch`** | `touch <name>` | 빈 File Inode 생성 및 매핑 (`TOUCH`) | `DirectoryEntryDto` | 크기가 0인 빈 파일 생성 |
| **`rm`** | `rm <name>` | Inode 및 Block 회수, 매핑 제거 (`RM`) | - (성공 메시지) | 파일 또는 (비어있는) 디렉터리 삭제 |
| **`write`** | `write <name> <text>` | 파일 크기에 맞춰 Block 할당 후 기록 (`WRITE`) | `Integer` (기록된 바이트 수) | 파일에 문자열 덮어쓰기 (용량 초과 시 예외 처리) |
| **`cat`** | `cat <name>` | 할당된 Block들을 순회하며 데이터 읽기 (`CAT`) | `FileContentDto` | 파일 내부의 문자열 데이터 출력 |
| **`tree`** | `tree [path]` | 경로 하위의 모든 Inode 재귀 탐색 (`TREE`) | `TreeNodeDto` | 디렉터리 구조를 계층적 트리 형태로 출력 |

*(참고: 상대경로인 `.`(현재)과 `..`(상위) 처리 로직은 `FileSystemManager.resolvePath()` 유틸리티 메서드에 구현하여 통합 처리합니다.)*
