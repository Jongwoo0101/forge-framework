# ForgeFramework

**Operating System Kernel Architecture Simulator Engine**

> 🇺🇸 English Documentation (한국어 버전: [README_KR.md](README_KR.md))

ForgeFramework is **not a real operating system kernel** that directly controls hardware. Instead, it is an **object-oriented simulation engine** that recreates the internal architecture of an operating system.

Rather than implementing concepts such as Process, Scheduler, Memory, File System, Interrupts, Devices, and Deadlocks as isolated algorithms, ForgeFramework aims to integrate them into **a cohesive operating system simulation** that behaves as a unified system.

---

# Ecosystem

ForgeFramework serves as the core engine of the ForgeOS ecosystem, supporting multiple user environments.

```text
                       ForgeFramework
                              ▲
                              │
                ┌─────────────┼─────────────┐
                │             │             │
                │             │             │
           ForgeOS         ForgeCLI     ForgeStudio
```

- **ForgeFramework** — Operating system simulation engine (this repository)
- **ForgeOS** — GUI-based operating system simulator
- **ForgeCLI** — Command-line operating system simulator
- **ForgeStudio** — Educational visualization and experimentation platform

---

# Core Philosophy

Every operation **must pass through the Kernel**.

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

Subsystems are never accessed directly.

For example, communication such as:

```
Shell → Scheduler
```

is not allowed.

Instead, every request follows:

```
Shell → System Call → Kernel → Scheduler
```

The Kernel acts as the **single entry point** and the sole coordinator of all subsystem managers.

---

# Overall Architecture

```text
                 User / Application
                        │
                 ForgeShell / CLI
                        │
                 System Call Layer
                        │
                   Forge Kernel
          ────────────────────────────
          │            │            │
          ▼            ▼            ▼
      Process      Memory      FileSystem
      Manager      Manager       Manager
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

# Design Principles

- Object-Oriented Design first
- Follow SOLID principles whenever possible
- Every class should have a single responsibility
- Heavy use of design patterns
- Designed for scalability and maintainability
- Architecture inspired by real operating systems

---

# Design Patterns

| Component | Pattern |
|-----------|---------|
| Kernel | Singleton, Facade |
| Scheduler | Strategy |
| Shell Commands | Command |
| Interrupt / Logger | Observer |
| PCB Creation | Factory |
| Process State | State |
| File System | Composite |
| Unknown Commands | Null Object |

---

# Programming Language

### Primary

- Java 21

### Optional

Performance-critical components may later be reimplemented in **C/C++ using JNI**, while keeping the Java API unchanged.

The current implementation is entirely Java-based.

---

# Development Roadmap

## ✅ Phase 1 — Core Foundation (Completed)

### Boot Manager

Implements the operating system boot sequence.

- Hardware Check
- Logger Initialization
- Kernel Initialization
- Subsystem Initialization
- Shell Ready

### Kernel

- Singleton Pattern
- Facade Pattern
- Centralized System Call Entry Point

### Event Logger

- Observer Pattern
- Console Event Listener

### Command System

- Command Pattern
- Null Object Pattern

---

## 🚧 Phase 2 — Process Management (Current)

### Process Manager

- Process Creation
- Process Termination
- Process State Management
- Context Switching

### Process

- PCB (Process Control Block)
- Process
- Process States

### Scheduler

Currently implemented

- FCFS
- Round Robin (Time Quantum = 3 Ticks)

Planned

- SJF
- SRTF
- Priority Scheduling
- Priority Aging
- MLFQ
- Linux CFS (Experimental)

### Hardware Timer

- Background thread-based virtual timer interrupt generator

---

## 🔜 Phase 3 — Memory Management

- Heap
- Stack
- Physical Memory
- Virtual Memory
- Paging
- Page Table
- Frame Table
- TLB
- Swap
- Page Fault Handling

---

## 🔜 Phase 4 — File System

- Virtual Disk (disk.img)
- Super Block
- Bitmap
- inode
- Directory
- Data Block

Planned shell commands

- ls
- mkdir
- touch
- rm
- cat
- write
- tree

---

## 🔜 Phase 5 — Devices & Interrupts

### Devices

- Keyboard
- Disk
- Printer
- Timer

### Interrupts

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
- API Refinement
- Integration Testing
- Performance Optimization
- ForgeFramework 1.0 Release

---

# Package Structure

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

Additional packages planned in future phases:

```text
memory
filesystem
interrupt
device
deadlock
```

---

# Supported Commands (Phase 2)

| Command | Description |
|----------|-------------|
| help | Display available commands |
| uptime | Display kernel uptime |
| ps | Show running and waiting processes |
| exec <name> | Create a new process |
| kill <pid> | Terminate a process |
| shutdown | Shut down the simulator |

Planned commands

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

# Build & Run

Requires **JDK 21** or later.

### Bash

```bash
# Compile
javac -d out $(find src/main/java -name "*.java")

# Run
java -cp out forgeframework.Main
```

Or use the provided scripts.

```bash
./scripts/build.sh
./scripts/run.sh
```

---

# Development Workflow

Every feature follows the same development cycle.

```text
Design
   ↓
Review
   ↓
Implementation
   ↓
Refactoring
   ↓
Testing
```

ForgeFramework is developed incrementally, one phase at a time, instead of implementing everything at once.

---

# Future Ecosystem

Once ForgeFramework reaches a stable release, the following projects will be built on top of it.

| Project | Description |
|----------|-------------|
| ForgeOS | GUI-based operating system simulator |
| ForgeCLI | Command-line operating system simulator |
| ForgeStudio | Educational visualization platform |

All projects share the same **ForgeFramework** engine.

---

# License

This project is being developed as a personal and educational project.