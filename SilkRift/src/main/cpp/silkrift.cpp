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

#include <jni.h>
#include <IORedirects.hpp>
#include <MapsGhost.hpp>
#include <StealthCrashBypass.hpp>
#include <LinkerBypass.hpp>
#include "Log.hpp"

extern "C"
JNIEXPORT void JNICALL
Java_eternal_future_silkrift_IORedirects_add_1entry(JNIEnv *env, jclass clazz, jstring from,
                                                    jstring to) {
    SilkRift::IORedirects::add_open_redirects(env->GetStringUTFChars(from, nullptr), env->GetStringUTFChars(to, nullptr));
}



extern "C"
JNIEXPORT jobject JNICALL
Java_eternal_future_silkrift_Core_getContext(JNIEnv *env, jclass clazz) {
    jclass activityThread = env->FindClass("android/app/ActivityThread");
    jmethodID currentActivityThread = env->GetStaticMethodID(activityThread, "currentActivityThread", "()Landroid/app/ActivityThread;");
    jobject at = env->CallStaticObjectMethod(activityThread, currentActivityThread);
    jmethodID getApplication = env->GetMethodID(activityThread, "getApplication", "()Landroid/app/Application;");
    jobject context = env->CallObjectMethod(at, getApplication);
    return context;
}
extern "C"
JNIEXPORT void JNICALL
Java_eternal_future_silkrift_IORedirects_init(JNIEnv *env, jclass clazz, jboolean open,
                                              jboolean openat, jboolean __openat) {
    SilkRift::IORedirects::open_enable = open;
    SilkRift::IORedirects::openat_enable = openat;
    SilkRift::IORedirects::__openat_enable = __openat;
    SilkRift::IORedirects::init();
}



extern "C"
JNIEXPORT void JNICALL
Java_eternal_future_silkrift_MapsGhost_init(JNIEnv *env, jclass clazz) {
    SilkRift::MapsGhost::init();
}
extern "C"
JNIEXPORT void JNICALL
Java_eternal_future_silkrift_MapsGhost_add_1entry(JNIEnv *env, jclass clazz, jstring entry) {
    SilkRift::MapsGhost::add_ghost_entry(env->GetStringUTFChars(entry, nullptr));
}
extern "C"
JNIEXPORT void JNICALL
Java_eternal_future_silkrift_Core_StealthCrashBypass_1Install(JNIEnv *env, jclass clazz) {
    StealthCrashBypass::Install();
}
extern "C"
JNIEXPORT void JNICALL
Java_eternal_future_silkrift_Core_LinkerBypass(JNIEnv *env, jclass clazz) {
    SilkRift::Linker::Config config;
    config.bypass_types = {
            SilkRift::Linker::BypassType::NAMESPACE,
            SilkRift::Linker::BypassType::PATH,
            SilkRift::Linker::BypassType::RELATIVE
    };
    config.safe_mode = true;
    config.max_retry_count = 5;

    SilkRift::LinkerBypass bypass(config);
    if (bypass.install()) {
        LOGD("Linker bypass installed successfully");
    }
}