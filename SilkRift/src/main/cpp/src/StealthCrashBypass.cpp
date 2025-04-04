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
#include <sys/ucontext.h>
#include <cstring>
#include <array>

void StealthCrashBypass::Install() {
    struct sigaction sa{};
    sa.sa_flags = SA_SIGINFO | SA_NODEFER;
    sa.sa_sigaction = SignalHandler;
    sigemptyset(&sa.sa_mask);

    sigaction(SIGSEGV, &sa, &originalSigSegv);
    sigaction(SIGILL, &sa, &originalSigIll);
    sigaction(SIGTRAP, &sa, &originalSigTrap);
}

void StealthCrashBypass::Uninstall() {
    sigaction(SIGSEGV, &originalSigSegv, nullptr);
    sigaction(SIGILL, &originalSigIll, nullptr);
    sigaction(SIGTRAP, &originalSigTrap, nullptr);
}

void StealthCrashBypass::SignalHandler(int sig, siginfo_t* info, void* ucontext) {
    void* crashAddr = info->si_addr;

    int attemptCount = 0;
    {
        std::lock_guard<std::mutex> lock(crashMutex);
        attemptCount = ++crashRecords[crashAddr];
    }

    int skipDistance = INITIAL_SKIP_DISTANCE * attemptCount;

    if (attemptCount > MAX_SKIP_ATTEMPTS) {
        struct sigaction original{};
        switch(sig) {
            case SIGSEGV: original = originalSigSegv; break;
            case SIGILL: original = originalSigIll; break;
            case SIGTRAP: original = originalSigTrap; break;
        }

        if (original.sa_sigaction) {
            original.sa_sigaction(sig, info, ucontext);
        }
        return;
    }

    auto* uc = (ucontext_t*)ucontext;
#if defined(__x86_64__)
    uc->uc_mcontext.gregs[REG_RIP] += skipDistance;
#elif defined(__aarch64__)
    uc->uc_mcontext.pc += skipDistance;
#elif defined(__arm__)
    uc->uc_mcontext.arm_pc += skipDistance;
#endif

    thread_local std::array<char, MAX_LOG_LENGTH> logBuffer;
    snprintf(logBuffer.data(), logBuffer.size(),
             "Crash#%d at %p (skip %d bytes, signal %d)",
             attemptCount, crashAddr, skipDistance, sig);
    LOGI("%s", logBuffer.data());
}