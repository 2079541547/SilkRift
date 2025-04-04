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

#include <SysCallHook.hpp>
#include <Log.hpp>
#include <dobby.h>

bool SilkRift::SyscallHook::init() {
    bool results = true;

    void* syscall = DobbySymbolResolver("libc.so", "syscall");

    results = DobbyHook(syscall, (void*)syscall_handler, (void**)&syscall_org);

    initialize = true;

    return results;
}

void
SilkRift::SyscallHook::register_handler(long num, SilkRift::SyscallHook::SyscallHandler handler) {
    handlers[num] = handler;
    if (!initialize) init();
}

void SilkRift::SyscallHook::unregister_handler(long num) {
    handlers.erase(num);
}

long SilkRift::SyscallHook::syscall_handler(long num, ...) {
    va_list args;
    va_start(args, num);

    long result = -1;

    if (handlers.find(num) != handlers.end()) {
        auto handler = handlers[num];
        LOGI("[SysCall] Handling #%ld via registered handler", num);
        result = handler(num, args);
        va_end(args);
    } else {
        result = syscall_org(num,
                              va_arg(args, long),
                              va_arg(args, long),
                              va_arg(args, long),
                              va_arg(args, long),
                              va_arg(args, long),
                              va_arg(args, long));
        va_end(args);
    }

    return result;
}