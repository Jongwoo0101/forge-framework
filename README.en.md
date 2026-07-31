# ForgeFramework

**Operating System Kernel Architecture Simulator Engine**

> 🇺🇸 English documentation (한국어 문서: [README.ko.md](README.ko.md))

ForgeFramework is the core engine of the ForgeOS ecosystem. It is not a kernel that controls real hardware, but a **framework that reproduces the internal architecture of an operating system using object-oriented design**.

Rather than implementing OS-course concepts — Process, Scheduler, Memory, File System, Interrupt, Device, Deadlock — as isolated exercises, the goal is to make them **behave together as a single, coherent operating system engine**.

## Ecosystem Structure

```text
ForgeFramework
▲
│
┌─────────────┼─────────────┐
│             │             │
│             │             │
ForgeOS         ForgeCLI     ForgeStudio
```

- **ForgeFramework**: The core operating system engine.
- **ForgeOS**: GUI-based operating system simulator.
- **ForgeCLI**: CLI-based operating system simulator (Developer environment).
- **ForgeStudio**: OS education and visualization tool.

*Note: This repository contains **ForgeFramework**.*

## Core Philosophy

Every feature must go **through the Kernel**, and only the Kernel.

Shell/App → System Call → Kernel → Subsystem


Direct access such as `App → Scheduler` is never allowed. The Kernel is the single manager of every subsystem manager.

## Architecture Overview

```text
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

## Development Phases (ForgeFramework)

### ✅ Phase 1 — Core Foundation (Completed)
- Boot Manager: boot sequence (hardware check → logger init → kernel init → subsystem init → shell ready)
- Kernel: Singleton + Facade, handles system calls (`HELP`, `SHUTDOWN`, `UPTIME`)
- Event Logger: Observer-pattern-based logging
- Command System: Null Object pattern, Command pattern

### 🚧 Phase 2 — Process Management (In Progress)
- PCB (Process Control Block), Process State
- Process Manager (Ready, Waiting, Terminated Queues)
- Scheduler (FCFS, Round Robin initially)
- Context Switch & Timer

### 🔜 Phase 3 — Memory Management
- Heap, Stack, Physical/Virtual Memory, Paging, TLB, Page Fault

### 🔜 Phase 4 — File System
- Virtual Disk (disk.img), Super Block, Bitmap, Inode, Directory

### 🔜 Phase 5 — Device & Interrupt
- Keyboard, Disk, Timer Interrupts

### 🔜 Phase 6 — System Integration
- Deadlock Manager, System Call Expansion, Integration Testing (v1.0 Release)

## License
This project is developed for personal/educational project purposes.