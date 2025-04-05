# 🌀 SilkRift

*SilkRift 是一个专用于安卓程序的安全测试框架*

## 🔍 核心功能

### 📂 I/O 重定向系统 (IORedirects)

**实现原理：**

```cpp
// 三级拦截体系
DobbyHook(open_addr, open_hook, ...);    // libc
DobbyHook(openat_addr, openat_hook, ...); // libc
DobbyHook(__openat_addr, __openat_hook, ...); // libc
SyscallHook(SYS_openat, sys_open);  // syscall
```

**特色功能：**

- 动态路径替换 `/data/app/xxx → /data/data/xxx/modified.apk`
- 文件描述符重定向
- 智能冲突检测机制

### 👻 内存隐藏系统 (MapsGhost)

**实现原理：**

```cpp
// 创建虚拟/proc文件
maps_fd = syscall(SYS_memfd_create, "maps_ghost", MFD_CLOEXEC);

// 依赖文件重定向：
IORedirects::add_open_redirects("/proc/self/maps", maps_fd);
IORedirects::add_open_redirects(pm, maps_pid_fd);
IORedirects::add_content_processor("/proc/self/maps", ContentProcessing);
IORedirects::add_content_processor(pm, ContentProcessing);
...

// 动态注册需要过滤的敏感内容
SilkRift::MapsGhost::add_ghost_entry("entry1"); 
...

```

**优势：**

- 不依赖/proc直接修改
- 低内存占用设计

### 🛡️ 崩溃防护系统

**原理：**

```cpp
void SignalHandler(int sig, siginfo_t* info, void* ucontext) {
    // 智能跳转修复
    modify_ucontext(ucontext); 
    // 频率限制
    if (++crashRecords[faultAddr] > 5) {
        force_skip_instruction(); //跳过错误指令
    }
}
```

**支持信号类型：**


| 信号    | 处理方案   |
| ------- | ---------- |
| SIGSEGV | 堆栈修复   |
| SIGILL  | 指令集重置 |
| SIGTRAP | 线程恢复   |

## 🎭 APK全息伪装技术

### 🔄 工作流程

```java
1. 劫持PackageManager → 
2. 动态修改关键字段：
   - ApplicationInfo.sourceDir
   - PackageInfo.signatures
   - LoadedApk.mAppDir
...
```

### 📦 签名伪装方案

**多版本兼容实现：**

```java
// Android 9+使用SigningInfo
if (Build.VERSION.SDK_INT >= P) {
    patchSigningInfo(pkgInfo);
} 
// 传统签名方案
else {
    patchSignatures(pkgInfo);  
}
```

### 🔑 关键技术点

```cpp
// 动态修改内存中的路径信息
void safeSetApplicationInfoPaths(ApplicationInfo appInfo, String newPath) {
    setField(appInfo, "sourceDir", newPath);
    setField(appInfo, "publicSourceDir", newPath);
    // 处理Split APK
    if (Build.VERSION.SDK_INT >= P) {
        setField(appInfo, "mSplitSourceDirs", new String[]{newPath});
    }
}
```

## 📜 法律声明

**本项目仅供合法的安全研究和技术学习使用！**

⚠️ **本框架仅限用于：**

- 授权安全测试
- 应用兼容性调试
- 隐私保护研究

🚫 **严禁用于：**

- 破解正版软件
- 绕过支付系统
- 窃取用户数据

使用者需自行承担因滥用带来的法律后果。

## 🔗 相关资源

- [Dobby 框架文档](https://github.com/jmpews/Dobby)
- Android NDK 官方文档
- Linux 系统调用手册

---

**请务必遵守法律法规，仅将本项目用于合法的技术研究和学习目的！**
