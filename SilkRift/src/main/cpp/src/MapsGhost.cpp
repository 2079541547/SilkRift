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

#include <MapsGhost.hpp>
#include <Log.hpp>
#include <IORedirects.hpp>

#include <filesystem>
#include <sstream>
#include <unistd.h>
#include <syscall.h>
#include <fstream>
#include <linux/memfd.h>
#include <fcntl.h>

bool SilkRift::MapsGhost::init() {
    const std::string pm = "/proc/" + std::to_string(getpid()) + "/maps";
    bool ret = (IORedirects::initialize || IORedirects::init());

    maps_fd = syscall(SYS_memfd_create, "maps_ghost", MFD_CLOEXEC);
    maps_pid_fd = syscall(SYS_memfd_create, "maps_pid_ghost", MFD_CLOEXEC);

    if (maps_fd < 0 || maps_pid_fd < 0) {
        LOGE("Failed to create memfd (maps_fd:%d, maps_pid_fd:%d)", maps_fd, maps_pid_fd);
        return false;
    }

    IORedirects::add_open_redirects("/proc/self/maps", maps_fd);
    IORedirects::add_open_redirects(pm, maps_pid_fd);
    IORedirects::add_content_processor("/proc/self/maps", ContentProcessing);
    IORedirects::add_content_processor(pm, ContentProcessing);

    initialize = true;
    return LOGI("Init %s", ret ? "OK" : "FAIL"), ret;
}

void SilkRift::MapsGhost::add_ghost_entry(const std::string &entry) {
    if (!initialize) init();
    ghost_entry.push_back(entry);
    LOGD("Added ghost entry: %s", entry.c_str());
}

void SilkRift::MapsGhost::ContentProcessing(
        const std::string &path,
        open_type open_orig,
        openat_type openat_orig,
        __openat_type __openat_orig)
{
    int fd = -1;
    if (open_orig) {
        fd = open_orig(path.c_str(), O_RDONLY | O_CLOEXEC, 0);
    } else if (openat_orig) {
        fd = openat_orig(AT_FDCWD, path.c_str(), O_RDONLY | O_CLOEXEC, 0);
    } else if (__openat_orig) {
        fd = __openat_orig(AT_FDCWD, path.c_str(), O_RDONLY | O_CLOEXEC, 0);
    }

    if (fd < 0) {
        LOGE("Failed to open %s (errno: %d)", path.c_str(), errno);
        return;
    }

    std::string content;
    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        content.append(buf, n);
    }
    close(fd);

    std::string filtered;
    std::istringstream iss(content);
    size_t total_lines = 0;
    size_t filtered_lines = 0;
    std::string line;

    while (std::getline(iss, line)) {
        total_lines++;
        bool should_filter = std::any_of(ghost_entry.begin(), ghost_entry.end(),
                                         [&](const auto& e) { return line.find(e) != std::string::npos; });

        if (!should_filter) {
            filtered += line + "\n";
            filtered_lines++;
        }
    }

    if (filtered_lines == 0 && total_lines > 0) {
        LOGW("All lines filtered, keeping first 10 lines");
        std::istringstream iss_again(content);
        for (int i = 0; i < 10 && std::getline(iss_again, line); i++) {
            filtered += line + "\n";
        }
    }

    int& target_fd = (path == ("/proc/" + std::to_string(getpid()) + "/maps")) ? maps_pid_fd : maps_fd;

    if (target_fd <= 0) {
        target_fd = syscall(SYS_memfd_create,
                            (path.find("self") != std::string::npos) ? "maps_ghost" : "maps_pid_ghost",
                            MFD_CLOEXEC);
        if (target_fd < 0) {
            LOGE("memfd_create failed (errno: %d)", errno);
            return;
        }
    }

    if (ftruncate(target_fd, 0) < 0) {
        LOGE("Failed to truncate memfd (errno: %d)", errno);
        return;
    }

    ssize_t written = write(target_fd, filtered.data(), filtered.size());
    if (written < 0) {
        LOGE("Write failed (errno: %d)", errno);
    } else {
        lseek(target_fd, 0, SEEK_SET);
        LOGD("Processed %s: kept %zu/%zu lines (%zd bytes)",
             path.c_str(), filtered_lines, total_lines, written);
    }
}