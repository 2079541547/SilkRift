/*******************************************************************************
 * 项目名称: SilkRift
 * 创建时间: 2025/4/3
 * 作者: EternalFuture゙
 * Github: https://github.com/2079541547
 * 版权声明: Copyright © 2024 EternalFuture. All rights reserved.
 * 许可证: Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 *
 * 注意事项: 请严格遵守Apache License 2.0协议使用本代码。Apache License 2.0允许商业用途，无需额外授权。
 *******************************************************************************/

#include <StealthCrashBypass.hpp>
#include <Log.hpp>

#include <csignal>
#include <cstddef>
#include <ctime>
#include <cstring>
#include <unistd.h>
#include <ucontext.h>
#include <sys/mman.h>
#include <errno.h>

// 静态成员初始化
std::array<StealthCrashBypass::CrashRecord, StealthCrashBypass::MAX_CRASH_RECORDS> StealthCrashBypass::crashRecords;
std::array<void*, StealthCrashBypass::MAX_CRASH_RECORDS> StealthCrashBypass::crashAddresses;
std::atomic<size_t> StealthCrashBypass::recordCount{0};

struct sigaction StealthCrashBypass::originalSigSegv;
struct sigaction StealthCrashBypass::originalSigIll;
struct sigaction StealthCrashBypass::originalSigTrap;
struct sigaction StealthCrashBypass::originalSigAbrt;
struct sigaction StealthCrashBypass::originalSigBus;
struct sigaction StealthCrashBypass::originalSigFpe;

void StealthCrashBypass::Install() {
    struct sigaction sa{};
    sa.sa_flags = SA_SIGINFO | SA_NODEFER | SA_RESTART;
    sa.sa_sigaction = SignalHandler;
    sigemptyset(&sa.sa_mask);

    sigaction(SIGSEGV, &sa, &originalSigSegv);
    sigaction(SIGILL, &sa, &originalSigIll);
    sigaction(SIGTRAP, &sa, &originalSigTrap);
    sigaction(SIGABRT, &sa, &originalSigAbrt);
    sigaction(SIGBUS, &sa, &originalSigBus);
    sigaction(SIGFPE, &sa, &originalSigFpe);

    LOGI("StealthCrashBypass installed for SIGSEGV, SIGILL, SIGTRAP, SIGABRT, SIGBUS, SIGFPE");
}

void StealthCrashBypass::Uninstall() {
    sigaction(SIGSEGV, &originalSigSegv, nullptr);
    sigaction(SIGILL, &originalSigIll, nullptr);
    sigaction(SIGTRAP, &originalSigTrap, nullptr);
    sigaction(SIGABRT, &originalSigAbrt, nullptr);
    sigaction(SIGBUS, &originalSigBus, nullptr);
    sigaction(SIGFPE, &originalSigFpe, nullptr);

    LOGI("StealthCrashBypass uninstalled");
}

const char* StealthCrashBypass::GetSignalName(int sig) {
    switch(sig) {
        case SIGSEGV: return "SIGSEGV";
        case SIGILL: return "SIGILL";
        case SIGTRAP: return "SIGTRAP";
        case SIGABRT: return "SIGABRT";
        case SIGBUS: return "SIGBUS";
        case SIGFPE: return "SIGFPE";
        default: return "UNKNOWN";
    }
}

const char* StealthCrashBypass::GetSignalDescription(int sig) {
    switch(sig) {
        case SIGSEGV: return "Segmentation violation (invalid memory reference)";
        case SIGILL: return "Illegal instruction";
        case SIGTRAP: return "Trace/breakpoint trap";
        case SIGABRT: return "Process abort signal";
        case SIGBUS: return "Bus error (bad memory access)";
        case SIGFPE: return "Floating-point exception";
        default: return "Unknown signal";
    }
}

void StealthCrashBypass::LogMemoryAround(void* addr) {
    const int bytesToDump = 32;
    uintptr_t startAddr = reinterpret_cast<uintptr_t>(addr) - bytesToDump/2;
    uintptr_t endAddr = reinterpret_cast<uintptr_t>(addr) + bytesToDump/2;

    // 对齐到16字节边界
    startAddr = startAddr & ~0xF;
    endAddr = (endAddr + 0xF) & ~0xF;

    LOGI("║ 🧠 MEMORY DUMP AROUND %p:", addr);

    for (uintptr_t p = startAddr; p < endAddr; p += 16) {
        // 检查内存是否可读
        if (msync(reinterpret_cast<void*>(p & ~(sysconf(_SC_PAGESIZE)-1)),
                  sysconf(_SC_PAGESIZE), MS_ASYNC) == -1) {
            LOGI("║ %016x: [inaccessible memory]", p);
            continue;
        }

        char line[128];
        char ascii[17] = {0};
        snprintf(line, sizeof(line), "║ %016x:", p);

        for (int i = 0; i < 16; i++) {
            uintptr_t currAddr = p + i;
            if (currAddr >= reinterpret_cast<uintptr_t>(addr) &&
                currAddr < reinterpret_cast<uintptr_t>(addr) + sizeof(void*)) {
                snprintf(line + strlen(line), sizeof(line) - strlen(line),
                         " \033[1;31m%02x\033[0m", *reinterpret_cast<unsigned char*>(currAddr));
            } else {
                snprintf(line + strlen(line), sizeof(line) - strlen(line),
                         " %02x", *reinterpret_cast<unsigned char*>(currAddr));
            }

            unsigned char c = *reinterpret_cast<unsigned char*>(currAddr);
            ascii[i] = (c >= 32 && c < 127) ? c : '.';
        }

        snprintf(line + strlen(line), sizeof(line) - strlen(line), "  |%s|", ascii);
        LOGI("%s", line);
    }
}

void StealthCrashBypass::LogRegisterState(ucontext_t* uc) {
#if defined(__arm__)
    LOGI("║ 🏛️ REGISTER STATE (ARM)");
    LOGI("║ R0: 0x%08x  R1: 0x%08x  R2: 0x%08x  R3: 0x%08x",
         uc->uc_mcontext.arm_r0, uc->uc_mcontext.arm_r1,
         uc->uc_mcontext.arm_r2, uc->uc_mcontext.arm_r3);
    LOGI("║ R4: 0x%08x  R5: 0x%08x  R6: 0x%08x  R7: 0x%08x",
         uc->uc_mcontext.arm_r4, uc->uc_mcontext.arm_r5,
         uc->uc_mcontext.arm_r6, uc->uc_mcontext.arm_r7);
    LOGI("║ R8: 0x%08x  R9: 0x%08x R10: 0x%08x FP: 0x%08x",
         uc->uc_mcontext.arm_r8, uc->uc_mcontext.arm_r9,
         uc->uc_mcontext.arm_r10, uc->uc_mcontext.arm_fp);
    LOGI("║ IP: 0x%08x  SP: 0x%08x  LR: 0x%08x  PC: 0x%08x",
         uc->uc_mcontext.arm_ip, uc->uc_mcontext.arm_sp,
         uc->uc_mcontext.arm_lr, uc->uc_mcontext.arm_pc);
    LOGI("║ CPSR: 0x%08x", uc->uc_mcontext.arm_cpsr);
#elif defined(__aarch64__)
    LOGI("║ 🏛️ REGISTER STATE (ARM64)");
    for (int i = 0; i < 31; i += 4) {
        LOGI("║ X%02d: 0x%016lx  X%02d: 0x%016lx  X%02d: 0x%016lx  X%02d: 0x%016lx",
             i, uc->uc_mcontext.regs[i],
             i+1, uc->uc_mcontext.regs[i+1],
             i+2, uc->uc_mcontext.regs[i+2],
             i+3, uc->uc_mcontext.regs[i+3]);
    }
    LOGI("║ PC: 0x%016lx  SP: 0x%016lx  PSTATE: 0x%016lx",
         uc->uc_mcontext.pc, uc->uc_mcontext.sp, uc->uc_mcontext.pstate);
#elif defined(__i386__)
    LOGI("║ 🏛️ REGISTER STATE (x86)");
    LOGI("║ EAX: 0x%08x  EBX: 0x%08x  ECX: 0x%08x  EDX: 0x%08x",
         uc->uc_mcontext.gregs[REG_EAX], uc->uc_mcontext.gregs[REG_EBX],
         uc->uc_mcontext.gregs[REG_ECX], uc->uc_mcontext.gregs[REG_EDX]);
    LOGI("║ ESI: 0x%08x  EDI: 0x%08x  EBP: 0x%08x  ESP: 0x%08x",
         uc->uc_mcontext.gregs[REG_ESI], uc->uc_mcontext.gregs[REG_EDI],
         uc->uc_mcontext.gregs[REG_EBP], uc->uc_mcontext.gregs[REG_ESP]);
    LOGI("║ EIP: 0x%08x  EFL: 0x%08x",
         uc->uc_mcontext.gregs[REG_EIP], uc->uc_mcontext.gregs[REG_EFL]);
#elif defined(__x86_64__)
    LOGI("║ 🏛️ REGISTER STATE (x86_64)");
    LOGI("║ RAX: 0x%016lx  RBX: 0x%016lx  RCX: 0x%016lx  RDX: 0x%016lx",
         uc->uc_mcontext.gregs[REG_RAX], uc->uc_mcontext.gregs[REG_RBX],
         uc->uc_mcontext.gregs[REG_RCX], uc->uc_mcontext.gregs[REG_RDX]);
    LOGI("║ RSI: 0x%016lx  RDI: 0x%016lx  RBP: 0x%016lx  RSP: 0x%016lx",
         uc->uc_mcontext.gregs[REG_RSI], uc->uc_mcontext.gregs[REG_RDI],
         uc->uc_mcontext.gregs[REG_RBP], uc->uc_mcontext.gregs[REG_RSP]);
    LOGI("║ R8:  0x%016lx  R9:  0x%016lx  R10: 0x%016lx  R11: 0x%016lx",
         uc->uc_mcontext.gregs[REG_R8], uc->uc_mcontext.gregs[REG_R9],
         uc->uc_mcontext.gregs[REG_R10], uc->uc_mcontext.gregs[REG_R11]);
    LOGI("║ R12: 0x%016lx  R13: 0x%016lx  R14: 0x%016lx  R15: 0x%016lx",
         uc->uc_mcontext.gregs[REG_R12], uc->uc_mcontext.gregs[REG_R13],
         uc->uc_mcontext.gregs[REG_R14], uc->uc_mcontext.gregs[REG_R15]);
    LOGI("║ RIP: 0x%016lx  EFL: 0x%016lx",
         uc->uc_mcontext.gregs[REG_RIP], uc->uc_mcontext.gregs[REG_EFL]);
#endif
}

void StealthCrashBypass::LogCrashDetails(int sig, siginfo_t* info, void* ucontext) {
    time_t now = time(nullptr);
    struct tm* tm_info = localtime(&now);
    char timestamp[20];
    strftime(timestamp, sizeof(timestamp), "%Y-%m-%d %H:%M:%S", tm_info);

    LOGI("╔═══════════════════════════════════════════════════════════════");
    LOGI("║ 🚨 CRASH DETECTED");
    LOGI("╠═══════════════════════════════════════════════════════════════");
    LOGI("║ 📅 Timestamp: %s", timestamp);
    LOGI("║ 🧵 Thread ID: %d", gettid());
    LOGI("║ ⚠️ Signal: %s (%d) - %s",
         GetSignalName(sig), sig, GetSignalDescription(sig));
    LOGI("║ 🎯 Fault Address: %p", info->si_addr);

    const char* codeDesc = "";
    switch(info->si_code) {
        case SEGV_MAPERR: codeDesc = "Address not mapped to object"; break;
        case SEGV_ACCERR: codeDesc = "Invalid permissions for mapped object"; break;
        case ILL_ILLADR: codeDesc = "Illegal addressing mode"; break;
        case ILL_ILLTRP: codeDesc = "Illegal trap"; break;
        case ILL_PRVOPC: codeDesc = "Privileged opcode"; break;
        case ILL_PRVREG: codeDesc = "Privileged register"; break;
        case ILL_BADSTK: codeDesc = "Invalid stack alignment"; break;
        default: codeDesc = "Unknown code";
    }
    LOGI("║ 🔢 Code: %d (%s)", info->si_code, codeDesc);
    LOGI("╠═══════════════════════════════════════════════════════════════");

    LogRegisterState(static_cast<ucontext_t*>(ucontext));

    if (info->si_addr != nullptr) {
        LogMemoryAround(info->si_addr);
    }

    LOGI("╚═══════════════════════════════════════════════════════════════");
}

void StealthCrashBypass::SignalHandler(int sig, siginfo_t* info, void* ucontext) {
    void* crashAddr = info->si_addr;

    LogCrashDetails(sig, info, ucontext);

    size_t recordIndex = MAX_CRASH_RECORDS;
    for (size_t i = 0; i < recordCount.load(std::memory_order_relaxed); ++i) {
        if (crashAddresses[i] == crashAddr) {
            recordIndex = i;
            break;
        }
    }

    if (recordIndex == MAX_CRASH_RECORDS && recordCount.load(std::memory_order_relaxed) < MAX_CRASH_RECORDS) {
        recordIndex = recordCount.fetch_add(1, std::memory_order_relaxed);
        crashAddresses[recordIndex] = crashAddr;
    }

    if (recordIndex < MAX_CRASH_RECORDS) {
        auto& record = crashRecords[recordIndex];
        int attemptCount = record.attemptCount.fetch_add(1, std::memory_order_relaxed) + 1;
        record.lastSignal.store(sig, std::memory_order_relaxed);
        record.lastTimestamp.store(time(nullptr), std::memory_order_relaxed);

        int skipDistance = INITIAL_SKIP_DISTANCE * (1 << std::min(attemptCount - 1, MAX_SKIP_ATTEMPTS_LOG2));
        skipDistance = std::min(skipDistance, MAX_SKIP_DISTANCE);

        auto* uc = (ucontext_t*)ucontext;
        void* originalPC = nullptr;
#if defined(__x86_64__)
        originalPC = (void*)uc->uc_mcontext.gregs[REG_RIP];
        uc->uc_mcontext.gregs[REG_RIP] += skipDistance;
#elif defined(__aarch64__)
        originalPC = (void*)uc->uc_mcontext.pc;
        uc->uc_mcontext.pc += skipDistance;
#elif defined(__arm__)
        originalPC = (void*)uc->uc_mcontext.arm_pc;
        uc->uc_mcontext.arm_pc += skipDistance;
#endif

        LOGI("\nCrash Handling:");
        LOGI("Attempt: %d", attemptCount);
        LOGI("Original PC: %p", originalPC);
        LOGI("Skip Distance: %d bytes", skipDistance);
        LOGI("Action: Instruction skipped");
    }

    // PrintCrashStats();
}

void StealthCrashBypass::PrintCrashStats() {
    LOGI("\n===== Crash Bypass Statistics =====");
    LOGI("Total crash records: %zu", recordCount.load());

    for (size_t i = 0; i < recordCount.load(); ++i) {
        auto& record = crashRecords[i];
        time_t timestamp = record.lastTimestamp.load();
        char timeBuf[26];
        ctime_r(&timestamp, timeBuf);
        timeBuf[strlen(timeBuf)-1] = '\0'; // 去掉换行符

        LOGI("\nRecord #%zu:", i+1);
        LOGI("Fault Address: %p", crashAddresses[i]);
        LOGI("Total Attempts: %d", record.attemptCount.load());
        LOGI("Last Signal: %s (%d)", GetSignalName(record.lastSignal.load()), record.lastSignal.load());
        LOGI("Last Occurrence: %s", timeBuf);
    }

    LOGI("===================================");
}






#include <assert.h>

void triggerSegmentationFault() {
    *((volatile int *) nullptr) = 0;  // 强制触发 SIGSEGV
}

void triggerDivisionByZero() {
    volatile int zero = 0;
    volatile int result = 42 / zero; // 这将导致运行时错误(SIGFPE)
    LOGI("%d", result);
}

void triggerInvalidMemoryAccess() {
    // 尝试访问无效内存地址
    char *invalidPtr = (char *)0xdeadbeef; // 使用一个几乎肯定无效的地址
    *invalidPtr = 'A'; // 尝试写入数据到无效地址，可能会触发SIGSEGV
}

void triggerDereferenceInvalidPointer() {
    int *nullPtr = nullptr;
    // 乱解引用空指针
    if (nullPtr != nullptr) { // 这个条件永远不会为真，只是为了演示
        *nullPtr = 10; // 如果执行到这里，会导致程序崩溃
    } else {
        // 另一种方式：尝试使用未初始化或已释放的指针
        int *uninitializedPtr;
        //*uninitializedPtr = 20; // 危险：试图解引用未初始化的指针
        int *freedPtr = new int(30);
        delete freedPtr;
        //*freedPtr = 40; // 危险：试图解引用已经释放的指针
    }
}

void triggerErrors() {
    LOGI("Attempting to trigger various types of errors...");

    triggerSegmentationFault(); // 强制触发 SIGSEGV
    triggerDivisionByZero();    // 除0错误
    triggerInvalidMemoryAccess(); // 尝试访问无效内存地址
    triggerDereferenceInvalidPointer(); // 乱解引用空指针

    LOGI("This message will not be printed if the errors are triggered correctly.");
}


__attribute__((constructor)) static void AutoInstall() {
    StealthCrashBypass::Install();
    //triggerErrors();
}


