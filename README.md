# ForgeOS

**Operating System Kernel Architecture Simulator**

> 🇰🇷 한국어 문서 (English version: [README.en.md](README.en.md))

ForgeOS는 실제 하드웨어를 제어하는 커널이 아니라, **운영체제 내부 구조를 객체지향적으로 재현하는 시뮬레이터**입니다.
Process, Scheduler, Memory, File System, Interrupt, Device, Deadlock 등 운영체제 과목의 핵심 개념을 각각 따로 구현하는 것이 아니라, **하나의 유기적인 운영체제처럼 동작**하도록 만드는 것이 목표입니다.

## 핵심 철학

모든 기능은 반드시 **Kernel을 거쳐서만** 동작합니다.

```
Shell → System Call → Kernel → Subsystem
```

`Shell → Scheduler`처럼 서브시스템에 직접 접근하는 코드는 존재하지 않으며, Kernel은 모든 Manager의 유일한 관리자입니다.

## 전체 아키텍처

```
                 User
                  │
             ForgeShell
                  │
          System Call Layer
                  │
             Forge Kernel
     ─────────────────────────
     │          │           │
     ▼          ▼           ▼
 Process    Memory     FileSystem
 Manager    Manager      Manager
     │
     ▼
 Scheduler → Interrupt Manager → Device Manager → Event Logger
```

## 개발 원칙

- 객체지향 설계 최우선, SOLID 원칙 준수
- 클래스 하나에 여러 책임을 부여하지 않음
- 디자인 패턴 적극 활용
- 확장성 있고 유지보수 쉬운 구조
- 실제 운영체제 구조를 최대한 반영

## 적용 디자인 패턴

| 대상 | 패턴 |
|---|---|
| Kernel | Singleton, Facade |
| Scheduler | Strategy |
| Shell Command | Command |
| Interrupt / Logger | Observer |
| PCB 생성 | Factory |
| Process State | State |
| File System | Composite |
| 미등록 명령어 | Null Object |

## 개발 언어

- 기본: **Java 21**
- 선택: 성능이 중요한 부분은 추후 C/C++ + JNI로 교체 가능하도록 설계 (현재는 Java 중심)

## 진행 단계 (Phase)

### ✅ Phase 1 — 기반 (현재)

- Boot Manager: 부팅 시퀀스 (하드웨어 점검 → 로거 초기화 → 커널 초기화 → 서브시스템 초기화 → 쉘 준비)
- Kernel: Singleton + Facade, 시스템 콜 처리 (`HELP`, `SHUTDOWN`, `UPTIME`)
- Event Logger: Observer 패턴 기반 로깅 (콘솔 리스너 포함)
- ForgeShell: REPL 기반 CLI
- Command System: `help`, `shutdown`, `uptime`

### 🔜 Phase 2 이후 (예정)

- Process Manager (PCB, fork, exec, kill, wait, exit)
- Scheduler (FCFS, SJF, Round Robin, Priority, MLFQ)
- Memory Manager (Heap, Stack, Virtual Memory, Paging, TLB, Swap, Page Table)
- File System (disk.img, Super Block, Bitmap, inode, Directory, Data Block)
- Device Manager (Keyboard, Disk, Printer, Timer)
- Interrupt Manager
- Deadlock Manager (Banker's Algorithm, Detection, Recovery)

## 패키지 구조

```
forgeos
├── boot        # BootManager, BootStage
├── kernel      # Kernel (Singleton + Facade)
├── syscall     # SystemCallType/Request/Result
├── shell       # ForgeShell, ShellPrompt
├── command     # Command 패턴 (help, shutdown, uptime 등)
├── logger      # EventLogger (Observer 패턴)
├── common      # 공용 상수
└── exception   # ForgeOSException
```

Phase가 진행됨에 따라 `process`, `scheduler`, `memory`, `filesystem`, `interrupt`, `device`, `deadlock`, `util` 패키지가 추가될 예정입니다.

## 지원 명령어 (Phase 1 기준)

| 명령어 | 설명 |
|---|---|
| `help` | 사용 가능한 명령어 목록 출력 |
| `uptime` | 커널 가동 시간 출력 |
| `shutdown` | 시스템 종료 |

> `ps`, `top`, `kill`, `fork`, `exec`, `ls`, `mkdir`, `touch`, `rm`, `tree`, `cat`, `write`, `malloc`, `free`, `meminfo`, `scheduler`, `deadlock` 등은 해당 서브시스템이 구현되는 Phase에서 순차적으로 추가됩니다.

## 빌드 및 실행

JDK 21 이상이 필요합니다.

```bash
# 컴파일
javac -d out $(find src/main/java -name "*.java")

# 실행
java -cp out forgeos.Main
```

또는 제공된 스크립트를 사용할 수 있습니다.

```bash
./scripts/build.sh
./scripts/run.sh
```

## 개발 방식

모든 기능은 아래 순서를 따라 개발됩니다.

```
설계 → 검토 → 구현 → 리팩토링 → 테스트
```

한 번에 대량의 코드를 생성하지 않고, Phase 단위로 작은 단위씩 점진적으로 확장합니다.

## 라이선스

이 프로젝트는 개인/학습 목적의 팀 프로젝트로 개발되고 있습니다.
