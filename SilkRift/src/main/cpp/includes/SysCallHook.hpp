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

#include <unordered_map>

namespace SilkRift {

    class SyscallHook {
    private:
        using SyscallHandler = long(*)(long, va_list);
        static inline std::unordered_map<long, SyscallHandler> handlers;

        static long syscall_handler(long num, ...);
    public:
        inline static bool initialize = false;
        inline static long(*syscall_org)(long, ...);

        static bool init();
        static void register_handler(long num, SyscallHandler handler);
        static void unregister_handler(long num);
    };

}