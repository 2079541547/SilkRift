/*******************************************************************************
 * 项目名称: SilkRift
 * 创建时间: 2025/3/30
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

#include <IORedirects.hpp>
#include <Log.hpp>
#include <SysCallHook.hpp>
#include <syscall.h>
#include <unistd.h>
#include <fcntl.h>
#include <dobby.h>

bool SilkRift::IORedirects::init() {

    constexpr auto libc = "libc.so";

    bool results = true;

    void* open_addr = DobbySymbolResolver(libc, "open");
    void* openat_addr = DobbySymbolResolver(libc, "openat");
    void* __openat_addr = DobbySymbolResolver(libc, "__openat");

    if (open_enable) results += DobbyHook(open_addr, (void*)open_hook, (void**)&open_org);
    if (openat_enable) results += DobbyHook(openat_addr, (void*)openat_hook, (void**)&openat_org);
    if (__openat_enable) results += DobbyHook(__openat_addr, (void*)__openat_hook, (void**)&__openat_org);

    if (!SyscallHook::initialize) results += SyscallHook::init();

#if defined(__LP64__)
     SyscallHook::register_handler(SYS_openat, sys_open);
#else
     SyscallHook::register_handler(SYS_open, sys_open);
#endif

    if (results) LOGI("The I/O redirect initialization is complete");
    else LOGE("The I/O redirect initialization failed");

    initialize = true;

    return results;
}

void SilkRift::IORedirects::add_open_redirects(const std::string &from, const std::string &to) {
    if (from.empty() || to.empty()) {
        LOGE("Empty path | %s → %s", from.c_str(), to.c_str());
        return;
    }
    if (from == to) {
        LOGW("Self-redirect | %s", from.c_str());
        return;
    }

    if (redirectsMap_fd.find(from) != redirectsMap_fd.end()) {
        LOGE("Conflict: Path %s already exists in fd redirects", from.c_str());
        return;
    }

    auto [it, inserted] = redirectsMap.insert_or_assign(from, to);
    LOGI("%s redirect | %s → %s", inserted ? "Added" : "Updated", from.c_str(), to.c_str());

    if (!initialize && init()) {
        LOGI("Initialized I/O subsystem");
    }
}

void SilkRift::IORedirects::add_open_redirects(const std::string &from, int to) {
    if (redirectsMap.find(from) != redirectsMap.end()) {
        LOGE("Conflict: Path %s already exists in path redirects", from.c_str());
        return;
    }

    auto [it, inserted] = redirectsMap_fd.insert_or_assign(from, to);
    LOGI("%s redirect | %s → %d", inserted ? "Added" : "Updated", from.c_str(), to);

    if (!initialize && init()) {
        LOGI("Initialized I/O subsystem");
    }
}

void SilkRift::IORedirects::add_content_processor(const std::string &path,
                                                  const std::function<void(
                                                          const std::string&,
                                                          open_type, openat_type, __openat_type)>& processor) {
    contentProcessors[path] = processor;
    LOGI("Added content processor for %s", path.c_str());
}

void SilkRift::IORedirects::process_file_content(const std::string &path,
                                                 open_type open_orig,
                                                 openat_type openat_orig, __openat_type __openat_orig) {
    LOGD("Attempting to process file content for: %s", path.c_str());

    auto it = contentProcessors.find(path);
    if (it == contentProcessors.end()) {
        LOGD("No content processor registered for path: %s", path.c_str());
        return;
    }

    it->second(path, open_orig, openat_orig, __openat_orig);
}

int SilkRift::IORedirects::open_hook(const char *path, int flags, mode_t mode) {
    std::string pathStr(path);
    auto pathIt = redirectsMap.find(pathStr);
    if (pathIt != redirectsMap.end()) {
        const char* target = pathIt->second.c_str();
        process_file_content(pathStr, open_org);

        LOGD("(open) path redirect: %s -> %s (flags: 0x%x)", path, target, flags);

        return open_org(target, flags, mode);
    }

    auto fdIt = redirectsMap_fd.find(pathStr);
    if (fdIt != redirectsMap_fd.end()) {
        int targetFd = fdIt->second;
        process_file_content(pathStr, open_org);

        if ((flags & O_ACCMODE) != O_WRONLY) {
            LOGD("(open) fd redirect - dup: %s -> fd:%d (flags: 0x%x)", path, targetFd, flags);
            return dup(targetFd);
        }

        LOGD("(open) fd redirect - direct: %s -> fd:%d (flags: 0x%x)", path, targetFd, flags);
        return targetFd;
    }

    return open_org(path, flags, mode);
}

int SilkRift::IORedirects::openat_hook(int dirfd, const char *path, int flags, mode_t mode) {
    std::string pathStr(path);
    auto pathIt = redirectsMap.find(pathStr);
    if (pathIt != redirectsMap.end()) {
        const char* target = pathIt->second.c_str();
        process_file_content(pathStr, nullptr, openat_org);

        LOGD("(openat) path redirect: %s -> %s (flags: 0x%x)", path, target, flags);

        return openat_org(dirfd, target, flags, mode);
    }

    auto fdIt = redirectsMap_fd.find(pathStr);
    if (fdIt != redirectsMap_fd.end()) {
        int targetFd = fdIt->second;
        process_file_content(pathStr, nullptr, openat_org);

        if ((flags & O_ACCMODE) != O_WRONLY) {
            LOGD("(openat) fd redirect - dup: %s -> fd:%d (flags: 0x%x)", path, targetFd, flags);
            return dup(targetFd);
        }

        LOGD("(openat) fd redirect - direct: %s -> fd:%d (flags: 0x%x)", path, targetFd, flags);
        return targetFd;
    }

    return openat_org(dirfd, path, flags, mode);
}

int SilkRift::IORedirects::__openat_hook(int dirfd, const char* path, int flags, int mode) {
    std::string pathStr(path);
    auto pathIt = redirectsMap.find(pathStr);
    if (pathIt != redirectsMap.end()) {
        const char* target = pathIt->second.c_str();
        process_file_content(pathStr, nullptr, nullptr, __openat_org);

        LOGD("(__openat) path redirect: %s -> %s (flags: 0x%x)", path, target, flags);

        return __openat_org(dirfd, target, flags, mode);
    }

    auto fdIt = redirectsMap_fd.find(pathStr);
    if (fdIt != redirectsMap_fd.end()) {
        int targetFd = fdIt->second;
        process_file_content(pathStr, nullptr, nullptr, __openat_org);

        if ((flags & O_ACCMODE) != O_WRONLY) {
            LOGD("(__openat) fd redirect - dup: %s -> fd:%d (flags: 0x%x)", path, targetFd, flags);
            return dup(targetFd);
        }

        LOGD("(__openat) fd redirect - direct: %s -> fd:%d (flags: 0x%x)", path, targetFd, flags);
        return targetFd;
    }

    return __openat_org(dirfd, path, flags, mode);
}


long SilkRift::IORedirects::sys_open(long mun, va_list args) {


#if defined(__LP64__)
    int dirfd = va_arg(args, int);
    const char* path = va_arg(args, const char*);
    int flags = va_arg(args, int);
    mode_t mode = va_arg(args, mode_t);
#else
    const char* path = va_arg(args, const char*);
    int flags = va_arg(args, int);
    mode_t mode = va_arg(args, mode_t);
    return (long)open_hook(path, flags, mode);
#endif


    std::string pathStr(path);
    auto pathIt = redirectsMap.find(pathStr);
    if (pathIt != redirectsMap.end()) {
        const char* target = pathIt->second.c_str();
        process_file_content(pathStr, nullptr, openat_org);

        LOGD("(syscall) path redirect: %s -> %s (flags: 0x%x)", path, target, flags);
#if defined(__LP64__)
        return SyscallHook::syscall_org(mun, dirfd, target, flags, mode);
#else
        return SyscallHook::syscall_org(mun, flags, mode);
#endif
    }

    auto fdIt = redirectsMap_fd.find(pathStr);
    if (fdIt != redirectsMap_fd.end()) {
        int targetFd = fdIt->second;
        process_file_content(pathStr, nullptr, openat_org);

        if ((flags & O_ACCMODE) != O_WRONLY) {
            LOGD("(syscall) fd redirect - dup: %s -> fd:%d (flags: 0x%x)", path, targetFd, flags);
            return dup(targetFd);
        }

        LOGD("(syscall) fd redirect - direct: %s -> fd:%d (flags: 0x%x)", path, targetFd, flags);
        return targetFd;
    }
}