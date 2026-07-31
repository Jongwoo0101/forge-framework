# ForgeFramework

**Operating System Kernel Architecture Simulator Engine**

> 🇰🇷 한국어 문서 (English version: [README.md](README.md))

ForgeFramework는 ForgeOS 생태계의 핵심 엔진입니다. 실제 하드웨어를 제어하는 커널이 아니라, **운영체제 내부 구조를 객체지향적으로 재현하는 시뮬레이터 프레임워크**입니다.
Process, Scheduler, Memory, File System 등 운영체제 과목의 핵심 개념을 각각 따로 구현하는 것이 아니라, **하나의 유기적인 운영체제 엔진처럼 동작**하도록 만드는 것이 목표입니다.

## 생태계 구조

```text
ForgeFramework
▲
│
┌─────────────┼─────────────┐
│             │             │
│             │             │
ForgeOS         ForgeCLI     ForgeStudio
```

- **ForgeFramework**: 운영체제 엔진 (본 저장소)
- **ForgeOS**: GUI 기반 운영체제 시뮬레이터
- **ForgeCLI**: CLI 기반 운영체제 시뮬레이터
- **ForgeStudio**: 운영체제 교육 및 시각화 도구

## 핵심 철학

모든 기능은 반드시 **Kernel을 거쳐서만** 동작합니다.

Shell/App → System Call → Kernel → Subsystem


애플리케이션 계층에서 서브시스템(예: Scheduler)에 직접 접근하는 코드는 절대 허용되지 않으며, Kernel은 모든 Manager의 유일한 창구입니다.

## 전체 아키텍처

```
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

## 프레임워크 개발 로드맵

### ✅ Phase 1 - Core Foundation (완료)
- Boot Manager: 부팅 시퀀스 제어
- Kernel: Singleton + Facade, 시스템 콜 단일 진입점
- Event Logger: Observer 패턴 기반 전역 로깅 시스템
- Command 구조 기반 마련

### 🚧 Phase 2 - Process Management (현재 진행 중)
- PCB(Process Control Block), Process State 구현
- Process Manager 및 상태별 Queue 구현
- Scheduler 초기 구현 (FCFS, Round Robin)
- Timer 및 Context Switch 기반 마련

### 🔜 Phase 3 - Memory Management
- Heap, Stack, 가상 메모리, Paging, TLB, Page Fault 처리

### 🔜 Phase 4 - File System
- 가상 디스크(disk.img), Super Block, Inode, 파일 API

### 🔜 Phase 5 - Device & Interrupt
- 장치 관리 및 타이머/디스크/키보드 인터럽트 처리

### 🔜 Phase 6 - System Integration
- Deadlock 감지 및 복구, 전체 시스템 통합 및 프레임워크 1.0 릴리스

## 라이선스
이 프로젝트는 개인/학습 목적의  프로젝트로 개발되고 있습니다.