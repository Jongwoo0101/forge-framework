# ForgeFramework

**Operating System Kernel Architecture Simulator Engine**

> 🇺🇸 English Documentation (한국어 버전: [README.md](README.md))

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

## ✅ Phase 2 — Process Management (Completed)

### Process Manager

- Process creation (with configurable burst time)
- Process termination (manual `kill`, or automatic once burst time is exhausted)
- Process state management
- Context switching (driven by Timer Interrupt)

### Process

- PCB — pid, burst time, accumulated CPU time, state
- Process — wraps a PCB with a human-readable name
- Process States — NEW / READY / RUNNING / WAITING / TERMINATED

### Scheduler

Currently implemented

- FCFS
- Round Robin (Time Quantum = 3 Ticks)
- **Runtime inspection/switching via the `scheduler` command** — algorithms can be swapped without recompiling; any processes still waiting in the old scheduler's queue are safely migrated to the new one

Planned

- SJF
- SRTF
- Priority Scheduling
- Priority Aging
- MLFQ
- Linux CFS (Experimental)

### Hardware Timer

- Background thread-based virtual timer interrupt generator (1 tick = 1 second)
- Each tick is delivered from Kernel to ProcessManager and drives context switching and burst-completion detection

---

## ✅ Phase 3 — Memory Management (Completed)

### Memory Manager

- A Facade that combines physical memory, per-process heap, page table, and TLB
- Heap and page table are automatically registered when a process is created via `exec`, and automatically reclaimed on termination (manual `kill` or automatic burst completion)
- `malloc` only requests as many new physical frames as are actually needed (existing free blocks are reused first); if physical memory runs out mid-request, any frames already reserved for that request are rolled back

### Heap

- Free-list based first-fit allocator (`malloc` / `free`)
- Adjacent free blocks are automatically coalesced to minimize fragmentation

### Physical Memory & Paging

- Physical memory is simulated as a fixed-size frame array (16 frames × 4 bytes by default)
- Each process has its own Page Table mapping virtual pages to physical frames
- The `translate` command lets you inspect the virtual-to-physical address translation directly

### TLB

- An LRU cache (capacity 4 by default) for recent address translations
- Exposes hit/miss counters; hit ratio is shown via `meminfo`
- Only the terminated process's own entries are invalidated on process exit

> Stack, Swap, and Page Fault Handling are not implemented yet — pages are always allocated to physical memory immediately in the current design, so there is no notion of a page being "swapped out" yet. Frame Table and Virtual Memory were completed in Phase 3.5; Stack will be added separately later when needed.

---

## ✅ Phase 3.5 — Frame Table & Virtual Memory (Completed)

Of the items left over from Phase 3, these two could be implemented independently of File System (Phase 4), so they were finished first. (Swap / Page Fault Handling still require the Virtual Disk from Phase 4 first, so they remain deferred.)

### Frame Table

- Frame bookkeeping was split out into a dedicated `FrameTable` class that can reverse-look-up the owning process (pid) and mapped virtual page number from a frame number
- Lookups are always O(1) since the array index is the frame number itself
- The `frametable` command shows the full state of physical memory (allocated/free, owner, page) as a table

### Virtual Memory

- Introduced a `VirtualAddressSpace` class that bundles a process's Heap and PageTable into a single object
- Previously, `heaps` and `pageTables` were two separate parallel maps, which in theory could drift out of sync if only one of them got registered/released; now there is exactly one map entry per pid
- `malloc` now passes the `pageNumber` directly at the moment a frame is reserved, so the Frame Table always reflects accurate owner/page information
---

## ✅ Phase 4 — File System (Completed)

### FileSystemManager

- Doesn't control real hardware, but builds a virtual disk space in memory and simulates a UNIX-style Inode-based file system in an object-oriented way
- Owns path resolution: walks Inodes sequentially from the root, supporting both absolute paths (`/a/b/c`) and relative paths (`.`, `..`)
- A Facade responsible for disk formatting (initialization), and file/directory create, delete, read, and write operations
- **Stateless by design** — every method receives the CWD as an argument rather than storing it internally; CWD is owned solely by the Shell layer (`ShellContext`)

### Virtual Disk & Hardware Virtualization Layer

- `VirtualDisk` — a virtual disk made of a fixed-size Data Block array (16 blocks × 16 bytes by default). Works directly with `byte[]`, converting strings to bytes on write
- `SuperBlock` — holds file-system metadata: total block count, block size, total inode count, free block count, the root (`/`) inode number, etc.
- `Bitmap` — runs both an `InodeBitmap` and a `DataBlockBitmap` to track allocation state for inodes and data blocks. When resources are exhausted, it responds with a clear failure message rather than throwing

### Inode & Directory

- `Inode` — holds only metadata for a file/directory (`inodeNumber`, `type`, `size`, `directBlocks`). File names are not stored here
- `Directory` — a separate object owning the name ↔ inode-number mapping (`Map<String, Integer>`). Directories are themselves inodes, but for simulation convenience the Inode wraps this object
- `InodeType` — distinguishes `FILE` from `DIRECTORY`

### DTOs (Data Transfer Objects)

- `DirectoryEntryDto`, `FileListDto`, `FileContentDto`, `TreeNodeDto` — plain data structures the Kernel returns to the Command layer, following the same stateless-API design principle intended for future frontend integration

### Shell Integration

- `ShellContext` owns the CWD state (`String currentWorkingDirectory`) and passes it along with every system call
- `ShellPrompt` was reworked so the prompt reflects the CWD live, e.g. `forgeframework:/usr/local> `

> The virtual disk is currently a pure in-memory structure. Disk contents are lost when the process exits; persisting to an actual `disk.img` file is not implemented yet.

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
├── boot                    # BootManager, BootStage
├── command                 # Command Pattern
├── common                  # Global Constants
├── exception                # Global Exceptions
├── hardware                 # Virtual Hardware (HardwareTimer)
├── kernel                   # Kernel (Singleton + Facade)
├── logger                   # Observer-based Logger
├── memory                   # PhysicalMemory, FrameTable, VirtualAddressSpace, Heap, PageTable, Tlb
├── process                  # PCB, Process, ProcessManager, ProcessState
│   └── scheduler             # Scheduler (Strategy), FcfsScheduler, RoundRobinScheduler
├── filesystem                # VirtualDisk, SuperBlock, Bitmap, Inode, Directory, FileSystemManager, DTOs
├── shell                    # ForgeShell, ShellContext (CWD state), ShellPrompt
└── syscall                  # System Call Layer
```

Additional packages planned in future phases:

```text
interrupt
device
deadlock
```

---

# Supported Commands (Phase 4)

| Command | Description |
|----------|-------------|
| help | Display available commands |
| uptime | Display kernel uptime |
| ps | Show process list and state (PID/STATE/CPU_TIME/BURST/NAME) |
| exec \<name> [burstTime] | Create a new process; burstTime falls back to a default if omitted |
| kill \<pid> | Terminate a process |
| scheduler [fcfs\|rr] | Inspect the current scheduler, or switch algorithms at runtime |
| malloc \<pid> \<size> | Allocate memory on a process's heap |
| free \<pid> \<address> | Free an allocated block |
| meminfo | Show physical memory / per-process heap / TLB usage |
| translate \<pid> \<vaddr> | Translate a virtual address to a physical address (inspect Paging + TLB) |
| frametable | Show the physical frame table (per-frame owner/page mapping) |
| pwd | Print the current working directory (CWD) as an absolute path |
| cd \<path> | Change directory (supports absolute/relative paths, `.`, `..`) |
| ls [path] | List files/directories in the CWD or a given path |
| mkdir \<name> | Create a new directory |
| touch \<name> | Create an empty (0-byte) file; succeeds without error even if it already exists |
| rm \<name> | Delete a file or an empty directory |
| write \<name> \<text> | Overwrite a file's contents (fails cleanly on disk/inode exhaustion) |
| cat \<name> | Print a file's contents |
| tree [path] | Print the directory structure as a hierarchical tree |
| shutdown | Shut down the simulator |

Planned commands

- fork
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
