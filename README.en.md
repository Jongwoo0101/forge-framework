# ForgeOS

**Operating System Kernel Architecture Simulator**

> 🇺🇸 English documentation (한국어 문서: [README.md](README.md))

ForgeOS is not a kernel that controls real hardware. It is a **simulator that reproduces the internal architecture of an operating system using object-oriented design**.

Rather than implementing OS-course concepts — Process, Scheduler, Memory, File System, Interrupt, Device, Deadlock — as isolated exercises, the goal is to make them **behave together as a single, coherent operating system**.

## Core Philosophy

Every feature must go **through the Kernel**, and only the Kernel.

```
Shell → System Call → Kernel → Subsystem
```

Direct access such as `Shell → Scheduler` is never allowed. The Kernel is the single manager of every subsystem manager.

## Architecture Overview

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

## Development Principles

- Object-oriented design first, following SOLID principles
- No class carries more than one responsibility
- Design patterns are actively used
- Extensible, maintainable structure
- Reflects the structure of a real operating system as closely as possible

## Design Patterns Applied

| Target | Pattern |
|---|---|
| Kernel | Singleton, Facade |
| Scheduler | Strategy |
| Shell Command | Command |
| Interrupt / Logger | Observer |
| PCB creation | Factory |
| Process State | State |
| File System | Composite |
| Unregistered command | Null Object |

## Language

- Primary: **Java 21**
- Optional: performance-critical parts may later be replaced with C/C++ via JNI (Java-first for now)

## Development Phases

### ✅ Phase 1 — Foundation (current)

- Boot Manager: boot sequence (hardware check → logger init → kernel init → subsystem init → shell ready)
- Kernel: Singleton + Facade, handles system calls (`HELP`, `SHUTDOWN`, `UPTIME`)
- Event Logger: Observer-pattern-based logging (with a console listener)
- ForgeShell: REPL-based CLI
- Command System: `help`, `shutdown`, `uptime`

### 🔜 Upcoming Phases

- Process Manager (PCB, fork, exec, kill, wait, exit)
- Scheduler (FCFS, SJF, Round Robin, Priority, MLFQ)
- Memory Manager (Heap, Stack, Virtual Memory, Paging, TLB, Swap, Page Table)
- File System (disk.img, Super Block, Bitmap, inode, Directory, Data Block)
- Device Manager (Keyboard, Disk, Printer, Timer)
- Interrupt Manager
- Deadlock Manager (Banker's Algorithm, Detection, Recovery)

## Package Structure

```
forgeos
├── boot        # BootManager, BootStage
├── kernel      # Kernel (Singleton + Facade)
├── syscall     # SystemCallType/Request/Result
├── shell       # ForgeShell, ShellPrompt
├── command     # Command pattern (help, shutdown, uptime, ...)
├── logger      # EventLogger (Observer pattern)
├── common      # Shared constants
└── exception   # ForgeOSException
```

As phases progress, `process`, `scheduler`, `memory`, `filesystem`, `interrupt`, `device`, `deadlock`, and `util` packages will be added.

## Supported Commands (Phase 1)

| Command | Description |
|---|---|
| `help` | List available commands |
| `uptime` | Show kernel uptime |
| `shutdown` | Shut down the system |

> `ps`, `top`, `kill`, `fork`, `exec`, `ls`, `mkdir`, `touch`, `rm`, `tree`, `cat`, `write`, `malloc`, `free`, `meminfo`, `scheduler`, `deadlock`, etc. will be added progressively as their subsystems are implemented.

## Build & Run

Requires JDK 21+.

```bash
# Compile
javac -d out $(find src/main/java -name "*.java")

# Run
java -cp out forgeos.Main
```

Or use the provided scripts:

```bash
./scripts/build.sh
./scripts/run.sh
```

## Development Workflow

Every feature follows this order:

```
Design → Review → Implement → Refactor → Test
```

Large amounts of code are never generated at once; the system grows incrementally, phase by phase.

## License

This project is developed for personal/educational team-project purposes.
