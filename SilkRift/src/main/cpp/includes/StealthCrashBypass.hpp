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

#pragma once




#include <array>
#include <atomic>

class StealthCrashBypass {
public:
    static constexpr size_t MAX_CRASH_RECORDS = 32;
    static constexpr int INITIAL_SKIP_DISTANCE = 4;  // 初始跳过4字节
    static constexpr int MIN_JUMP_OUT_DISTANCE = INT32_MAX;
    static constexpr int MAX_SKIP_DISTANCE = INT32_MAX;     // 最大跳过 2^32 字节
    static constexpr int MAX_SKIP_ATTEMPTS_LOG2 = 32; // 最多尝试次数(2^32)

    struct CrashRecord {
        std::atomic<int> attemptCount{0};
        std::atomic<int> lastSignal{0};
        std::atomic<time_t> lastTimestamp{0};
    };

    static void Install();
    static void Uninstall();
    static void PrintCrashStats();

private:
    static void SignalHandler(int sig, siginfo_t* info, void* ucontext);
    static const char* GetSignalName(int sig);
    static const char* GetSignalDescription(int sig);
    static void LogCrashDetails(int sig, siginfo_t* info, void* ucontext);
    static void LogRegisterState(ucontext_t* uc);
    static void LogMemoryAround(void* addr);

    static std::array<CrashRecord, MAX_CRASH_RECORDS> crashRecords;
    static std::array<void*, MAX_CRASH_RECORDS> crashAddresses;
    static std::atomic<size_t> recordCount;

    static struct sigaction originalSigSegv;
    static struct sigaction originalSigIll;
    static struct sigaction originalSigTrap;
    static struct sigaction originalSigAbrt;
    static struct sigaction originalSigBus;
    static struct sigaction originalSigFpe;
};