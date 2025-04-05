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

#pragma once

#include <string>
#include <unordered_map>
#include <functional>
#include <vector>

namespace SilkRift {

    using open_type = int (*)(const char*, int, mode_t);
    using openat_type = int (*)(int, const char*, int, mode_t);
    using __openat_type = int (*)(int, const char*, int, int);

    class IORedirects {
    private:
        inline static open_type open_org;
        inline static openat_type openat_org;
        inline static __openat_type __openat_org;

        inline static std::unordered_map<std::string, std::string> redirectsMap;
        inline static std::unordered_map<std::string, int> redirectsMap_fd;
        inline static std::unordered_map<std::string, std::function<void(const std::string&,
                open_type, openat_type, __openat_type)>> contentProcessors;

        inline static std::unordered_map<std::string, std::string> redirectsDirectoryMap;

        static int open_hook(const char* path, int flags, mode_t mode);
        static int openat_hook(int dirfd, const char* path, int flags, mode_t mode);
        static int __openat_hook(int dirfd, const char* path, int flags, int mode);

        static long sys_open(long mun, va_list args);

        static void process_file_content(const std::string &path,
                                         open_type open_orig = nullptr,
                                         openat_type openat_orig = nullptr,
                                         __openat_type __openat_orig = nullptr);
    public:

        inline static bool open_enable = false,
        openat_enable = false,
        __openat_enable = true,
        initialize = false;

        static bool init();
        static void add_open_redirects(const std::string &from, const std::string &to);
        static void add_open_redirects(const std::string &from, int to);
        static void add_content_processor(const std::string& path, const std::function<void(const std::string&, open_type, openat_type, __openat_type)>& processor);
        
        static void add_directory_redirects(const std::string &from, const std::string &to);
    };

}