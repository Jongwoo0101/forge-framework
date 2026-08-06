# ForgeFramework Phase 5 개발 명세서: Device & Interrupt

## 1. 개요 및 목표

운영체제의 하드웨어 제어 추상화(Device Management)와 비동기 이벤트 처리 메커니즘(Interrupt Handling)을 시뮬레이션합니다. 폴링(Polling) 방식이 아닌 **인터럽트 기반(Interrupt-driven)의 입출력 처리**를 구현하여, 프로세스의 `RUNNING` ➔ `WAITING` ➔ `READY` 상태 전이를 완벽하게 동작시키는 것이 핵심 목표입니다.

## 2. 아키텍처 및 핵심 컴포넌트 설계

### 2.1. Interrupt 계층 (`forgeframework.interrupt`)

모든 하드웨어 및 소프트웨어 이벤트의 중앙 라우터 역할을 합니다.

* **`InterruptType` (Enum):**
* `TIMER`: 일정 틱(Tick)마다 발생하는 하드웨어 타이머 인터럽트.
* `KEYBOARD`: 사용자가 문자열을 입력했을 때 발생하는 인터럽트.
* `DISK`: 디스크 I/O 작업이 완료되었을 때 발생하는 인터럽트.
* `SOFTWARE`: 프로세스가 자발적으로 발생시키거나(예: 예외 발생, 강제 종료 시그널 등) OS 내부 통신용 인터럽트.


* **`InterruptRequest` (DTO):**
* 인터럽트 타입과 페이로드(예: 키보드 입력된 문자열, 완료된 디스크 작업의 PID 등)를 담는 불변 객체.


* **`InterruptManager` (IDT - Interrupt Descriptor Table 역할):**
* 인터럽트 타입별로 등록된 `InterruptHandler`(콜백 함수)를 매핑하고 실행하는 중앙 관리자.



### 2.2. Device 계층 (`forgeframework.device`)

OS가 하드웨어를 추상화하여 관리하는 계층입니다.

* **`Device` (Interface):**
* 모든 장치가 구현해야 할 표준 인터페이스 (`read()`, `write()`, `status()`).


* **`DeviceManager`:**
* 커널 부팅 시 모든 장치(Keyboard, Disk, Printer, Timer)를 초기화하고 레지스트리에 등록하는 관리자.


* **구체적인 장치 클래스들:**
* **`KeyboardDevice`:** 내부 버퍼를 가집니다. 외부에서 입력이 들어오면 버퍼에 저장하고 `KEYBOARD` 인터럽트를 발생시킵니다.
* **`DiskDevice`:** Phase 4의 `VirtualDisk`를 감싸는 래퍼(Wrapper)입니다. I/O 요청 시 대기 큐(Wait Queue)에 넣고, 처리 완료 시 `DISK` 인터럽트를 발생시킵니다.
* **`PrinterDevice`:** 출력 요청을 받아 스풀링(Spooling) 큐에 넣고 일정 시간 후 처리 완료를 알립니다.
* **`TimerDevice`:** 기존 `HardwareTimer`를 이 계층으로 편입합니다. 직접 `ProcessManager`를 호출하던 기존과 달리, 이제는 순수하게 `TIMER` 인터럽트만 `InterruptManager`로 쏘아 올립니다.



## 3. 커널 및 프로세스 매니저의 변화 (Integration)

Phase 5의 가장 중요한 변화는 **프로세스 상태 전이**입니다.

* **`ProcessManager.waitProcess(pid)`:**
* 프로세스가 I/O를 요청하면, 커널은 이 메서드를 호출해 프로세스의 상태를 `RUNNING`에서 `WAITING`으로 변경하고 CPU를 빼앗아(Context Switch) 다른 프로세스에게 줍니다.


* **`ProcessManager.wakeupProcess(pid)`:**
* 인터럽트 핸들러가 I/O 완료를 감지하면 이 메서드를 호출해 프로세스를 `WAITING`에서 `READY` 큐로 다시 올려보냅니다.



## 4. DTO 설계 (Data Transfer Object)

* **`DeviceInfoDto(String name, String type, String status)`:** `devinfo` 명령 시 반환할 장치 상태 객체.
* **`InterruptLogDto(String time, String type, String details)`:** 발생한 인터럽트 내역을 추적하기 위한 객체.

## 5. 시스템 콜 및 명령어(Command) 명세

| 명령어 (Shell) | SystemCall | 반환 DTO | 기능 설명 및 라이프사이클 |
| --- | --- | --- | --- |
| **`devinfo`** | `DEVINFO` | `List<DeviceInfoDto>` | 현재 커널에 등록된 장치(Keyboard, Disk 등)의 목록과 상태(대기 큐 크기 등) 출력 |
| **`io_request`** | `IO_REQ` | `String` (성공 메시지) | 특정 PID의 프로세스가 장치(keyboard/disk)에 I/O를 요청. **해당 프로세스는 즉시 `WAITING` 상태가 됨.** (사용법: `io_request <pid> <device>`) |
| **`type`** | `HW_INPUT` | `String` (입력 처리 결과) | (가상 하드웨어 조작) 키보드 하드웨어에 텍스트를 입력함. 내부적으로 **Keyboard Interrupt를 발생시키고, 키보드를 기다리던 프로세스를 `READY`로 깨움.** |
| **`disk_finish`** | `HW_DISK` | `String` | (가상 하드웨어 조작) 디스크 하드웨어가 작업을 마쳤음을 커널에 알림. 내부적으로 **Disk Interrupt를 발생시키고 대기 중이던 프로세스를 깨움.** |
| **`soft_int`** | `SW_INT` | `String` | 사용자가 강제로 **Software Interrupt**를 발생시켜 커널의 특정 동작(예: 캐시 플러시, 전체 프로세스 일시정지 등)을 테스트함. |

> **💡 설계 의도:** CLI 환경에서는 실제 하드웨어가 없기 때문에, 사용자가 직접 하드웨어의 역할을 대신하여 `type`이나 `disk_finish` 같은 명령어로 "외부 하드웨어 이벤트(인터럽트 발생)"를 흉내 내야 시뮬레이션이 가능합니다.

