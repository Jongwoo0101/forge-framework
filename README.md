# ForgeFramework

**Operating System Kernel Architecture Simulator Engine**

> 🇰🇷 한국어 문서 (English version: [README.en.md](README.en.md))

ForgeFramework는 실제 하드웨어를 제어하는 커널이 아니라, **운영체제 내부 구조를 객체지향적으로 재현하는 시뮬레이터 엔진**입니다.

Process, Scheduler, Memory, File System, Interrupt, Device, Deadlock 등 운영체제 과목의 핵심 개념을 각각 따로 구현하는 것이 아니라, **하나의 유기적인 운영체제처럼 동작**하도록 만드는 것이 목표입니다.

---

# 생태계 구조 (Ecosystem)

ForgeFramework는 다양한 사용자 환경을 지원하는 ForgeOS 생태계의 핵심 기반 엔진입니다.

```text
                       ForgeFramework
                              ▲
                              │
                ┌─────────────┼─────────────┐
                │             │             │
                │             │             │
           ForgeOS         ForgeCLI     ForgeStudio
```

- **ForgeFramework** : 운영체제 엔진 (본 저장소)
- **ForgeOS** : GUI 기반 운영체제 시뮬레이터
- **ForgeCLI** : CLI 기반 운영체제 시뮬레이터
- **ForgeStudio** : 운영체제 교육 및 시각화 도구

---

# 핵심 철학

모든 기능은 반드시 **Kernel**을 거쳐서만 동작합니다.

```text
Application / Shell
        │
        ▼
  System Call
        │
        ▼
     Kernel
        │
        ▼
   Subsystem
```

Shell → Scheduler처럼 애플리케이션이나 Shell 계층에서 Subsystem에 직접 접근하는 코드는 존재하지 않습니다.

Kernel은 모든 Manager의 유일한 관리자(Single Entry Point) 역할을 수행합니다.

---

# 전체 아키텍처

```text
                 User / App
                      │
              ForgeShell / CLI
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
     Scheduler
         │
         ▼
 Interrupt Manager
         │
         ▼
   Device Manager
         │
         ▼
    Event Logger
```

---

# 개발 원칙

- 객체지향 설계를 최우선으로 합니다.
- SOLID 원칙을 최대한 준수합니다.
- 하나의 클래스는 하나의 책임만 가집니다.
- 디자인 패턴을 적극적으로 활용합니다.
- 확장성과 유지보수성을 고려한 구조를 설계합니다.
- 실제 운영체제의 구조를 최대한 반영합니다.

---

# 적용 디자인 패턴

| 대상 | 패턴 |
|------|------|
| Kernel | Singleton, Facade |
| Scheduler | Strategy |
| Shell Command | Command |
| Interrupt / Logger | Observer |
| PCB 생성 | Factory |
| Process State | State |
| File System | Composite |
| 미등록 명령어 | Null Object |

---

# 개발 언어

- **기본:** Java 21
- **선택:** 성능이 중요한 모듈은 추후 C/C++ + JNI를 이용하여 교체 가능하도록 설계
- 현재는 Java 중심으로 개발합니다.

---

# 진행 단계 (Phase)

## ✅ Phase 1 — Core Foundation (완료)

### Boot Manager

- 부팅 시퀀스
    - Hardware Check
    - Logger Initialization
    - Kernel Initialization
    - Subsystem Initialization
    - Shell Ready

### Kernel

- Singleton
- Facade
- System Call 단일 진입점 구현

### Event Logger

- Observer Pattern
- Console Listener

### Command System

- Command Pattern
- Null Object Pattern

---

## 🚧 Phase 2 — Process Management (현재 진행 중)

### Process Manager

- Process 생성
- Process 종료
- Process State 관리
- Context Switch

### Process

- PCB
- Process
- Process State

### Scheduler

현재 구현

- FCFS
- Round Robin (Time Quantum = 3 Tick)

추후 구현

- SJF
- SRTF
- Priority
- Priority Aging
- MLFQ
- Linux CFS (Experimental)

### Hardware Timer

- Background Thread 기반 Timer Interrupt Generator

---

## 🔜 Phase 3 — Memory Management

- Heap
- Stack
- Virtual Memory
- Paging
- Page Table
- Frame Table
- TLB
- Swap
- Page Fault

---

## 🔜 Phase 4 — File System

- Virtual Disk (disk.img)
- Super Block
- Bitmap
- inode
- Directory
- Data Block

지원 예정 명령어

- ls
- mkdir
- touch
- rm
- cat
- write
- tree

---

## 🔜 Phase 5 — Device & Interrupt

### Device

- Keyboard
- Disk
- Printer
- Timer

### Interrupt

- Timer Interrupt
- Keyboard Interrupt
- Disk Interrupt
- Software Interrupt

---

## 🔜 Phase 6 — System Integration

- Deadlock Manager
- Banker's Algorithm
- Deadlock Detection
- Deadlock Recovery
- API 정리
- 통합 테스트
- 성능 최적화
- ForgeFramework 1.0 Release

---

# 패키지 구조

```text
forgeframework
├── boot          # BootManager, BootStage
├── command       # Command Pattern
├── common        # Global Constants
├── exception     # Global Exceptions
├── hardware      # Virtual Hardware
├── kernel        # Kernel (Singleton + Facade)
├── logger        # Observer-based Logger
├── process       # PCB, Process, Scheduler
├── shell         # ForgeShell
└── syscall       # System Call Layer
```

Phase가 진행됨에 따라 아래 패키지가 추가될 예정입니다.

```text
memory
filesystem
interrupt
device
deadlock
```

---

# 지원 명령어 (Phase 2 기준)

| 명령어 | 설명 |
|--------|------|
| help | 사용 가능한 명령어 출력 |
| uptime | 커널 가동 시간 출력 |
| ps | 프로세스 목록 출력 |
| exec \<name> | 새로운 프로세스 생성 |
| kill \<pid> | 프로세스 종료 |
| shutdown | 시스템 종료 |

향후 추가 예정

- fork
- scheduler
- malloc
- free
- meminfo
- ls
- mkdir
- touch
- rm
- cat
- write
- deadlock

---

# 빌드 및 실행

JDK 21 이상이 필요합니다.

### Bash

```bash
# Compile
javac -d out $(find src/main/java -name "*.java")

# Run
java -cp out forgeframework.Main
```

또는 제공된 스크립트를 사용할 수 있습니다.

```bash
./scripts/build.sh
./scripts/run.sh
```

---

# 개발 방식

모든 기능은 아래 순서를 따라 개발합니다.

```text
설계
   ↓
검토
   ↓
구현
   ↓
리팩토링
   ↓
테스트
```

한 번에 대량의 기능을 구현하지 않고, Phase 단위로 점진적으로 확장합니다.

---

# 향후 생태계 확장

ForgeFramework가 안정화되면 다음 프로젝트를 개발합니다.

| 프로젝트 | 설명 |
|----------|------|
| ForgeOS | GUI 기반 운영체제 시뮬레이터 |
| ForgeCLI | CLI 기반 운영체제 시뮬레이터 |
| ForgeStudio | 운영체제 학습 및 시각화 도구 |

모든 프로젝트는 **ForgeFramework**를 공통 엔진으로 사용합니다.

---

# License

이 프로젝트는 개인 및 학습 목적의 프로젝트로 개발되고 있습니다.