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

#include <csignal>
#include <unordered_map>
#include <mutex>
#include <atomic>

class StealthCrashBypass {
public:
    static void Install();
    static void Uninstall();

private:
    inline static void SignalHandler(int sig, siginfo_t* info, void* ucontext);

    inline static std::unordered_map<void*, std::atomic<int>> crashRecords;
    inline static std::mutex crashMutex;

    static constexpr int INITIAL_SKIP_DISTANCE = 2;
    static constexpr int MAX_SKIP_ATTEMPTS = 10;
    static constexpr int MAX_LOG_LENGTH = 256;

    inline static struct sigaction originalSigSegv;
    inline static struct sigaction originalSigIll;
    inline static struct sigaction originalSigTrap;
};