package eternal.future.silkrift.dome;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.pm.*;
import android.os.Process;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private TextView tvLog;
    private final StringBuilder logBuilder = new StringBuilder();
    private static final String EXPECTED_APPLICATION_CLASS = "android.app.Application";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvLog = findViewById(R.id.tv_log);

        printSignatureDetails();
        verifyApkIntegrity();
        // checkProcessMaps();

        checkManifestTampering();
        checkApplicationClass();
        checkDexInjections();
        checkClassInjections();
        checkNativeLibraries();
        checkDebuggableStatus();
        checkSignatureValid();

        appendLog("=== 安全检测完成 ===");
    }

    private void checkManifestTampering() {
        try {
            appendLog("\n[1] 扫描AndroidManifest.xml...");

            PackageManager pm = getPackageManager();
            PackageInfo pkgInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_ACTIVITIES);

            String mainActivity = getPackageName() + ".MainActivity";
            boolean foundMain = false;
            assert pkgInfo.activities != null;
            for (ActivityInfo activity : pkgInfo.activities) {
                if (activity.name.equals(mainActivity)) {
                    foundMain = true;
                    break;
                }
            }
            appendLog(foundMain ? "主Activity验证通过" : "警告：主Activity被篡改");

            assert pkgInfo.applicationInfo != null;
            XmlResourceParser parser = pm.getResourcesForApplication(pkgInfo.applicationInfo)
                    .getAssets().openXmlResourceParser("AndroidManifest.xml");

            int eventType;
            while ((eventType = parser.next()) != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("uses-permission".equals(tagName)) {
                        String perm = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
                        appendLog("声明权限: " + perm);
                    }
                }
            }
            parser.close();
        } catch (Exception e) {
            appendLog("Manifest扫描异常: " + e);
        }
    }

    private void checkApplicationClass() {
        try {
            appendLog("\n[2] 验证Application类及应用信息...");

            // 获取包信息
            String packageName = getPackageName();
            PackageInfo pkgInfo = getPackageManager().getPackageInfo(packageName, 0);
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(packageName, 0);

            // 打印基础信息
            appendLog("应用包名: " + packageName);
            appendLog("版本名称: " + pkgInfo.versionName);
            appendLog("版本号: " + pkgInfo.versionCode);

            // 检测共享UID（如果存在）
            if (appInfo.uid != Process.myUid()) {
                appendLog("警告：检测到共享UID (sharedUserId)，可能与其他应用共享数据");
            } else {
                appendLog("UID检测：正常（无共享UID）");
            }

            // 检测是否被调试
            if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                appendLog("警告：应用处于可调试模式 (android:debuggable=true)");
            } else {
                appendLog("调试状态：正常（未开启调试模式）");
            }

            // 检查Application类
            String appClass = appInfo.className != null ? appInfo.className : EXPECTED_APPLICATION_CLASS;
            appendLog("当前Application类: " + appClass);

            if (!EXPECTED_APPLICATION_CLASS.equals(appClass)) {
                appendLog("警告：检测到自定义Application类，可能被注入");

                try {
                    Class<?> clazz = Class.forName(appClass);
                    if (clazz.getClassLoader() != getClassLoader()) {
                        appendLog("危险：Application类从非常规类加载器加载");
                    }
                } catch (ClassNotFoundException e) {
                    appendLog("警告：无法加载声明的Application类");
                }
            } else {
                appendLog("Application类验证通过");
            }

        } catch (Exception e) {
            appendLog("应用信息检查异常: " + e);
        }
    }

    private void checkDexInjections() {
        try {
            appendLog("\n[3] 检查DEX注入...");

            String apkPath = getPackageCodePath();
            Set<String> officialDex = new HashSet<>();

            try (ZipFile zip = new ZipFile(apkPath)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".dex")) {
                        officialDex.add(entry.getName());
                    }
                }
            }

            boolean foundInjection = false;
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(".dex") && !line.contains(apkPath)) {
                        appendLog("发现可疑DEX映射: " + line);
                        foundInjection = true;
                    }
                }
            }

            appendLog(foundInjection ? "警告：检测到DEX注入" : "未检测到DEX注入");
        } catch (Exception e) {
            appendLog("DEX检查异常: " + e);
        }
    }

    private void checkClassInjections() {
        try {
            appendLog("\n[4] 检查类注入...");

            ClassLoader cl = getClassLoader();
            if (cl.toString().contains("InMemoryDexClassLoader")) {
                appendLog("警告：检测到内存DEX加载");
            }

            checkClassTampering("android.app.Activity");
            checkClassTampering("android.content.Context");
            checkClassTampering(getClass().getName());
        } catch (Exception e) {
            appendLog("类检查异常: " + e);
        }
    }

    private void checkClassTampering(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            String actualLocation = Objects.requireNonNull(clazz.getProtectionDomain())
                    .getCodeSource().getLocation().getPath();

            String expectedPath = getPackageCodePath();
            if (!actualLocation.equals(expectedPath)) {
                appendLog("警告：" + className + " 从非常规位置加载: " + actualLocation);
            } else {
                appendLog(className + " 验证通过");
            }
        } catch (Exception e) {
            appendLog(className + " 检查失败: " + e);
        }
    }

    // 5. Native库检测
    private void checkNativeLibraries() {
        try {
            appendLog("\n[5] 检查Native库...");

            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(), 0);
            File libDir = new File(appInfo.nativeLibraryDir);

            // 检查官方库
            String[] officialLibs = libDir.list();
            if (officialLibs != null) {
                for (String lib : officialLibs) {
                    appendLog("官方Native库: " + lib);
                }
            }

            boolean foundSuspicious = false;
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(".so") && line.contains(appInfo.nativeLibraryDir) && line.contains("data")) {
                        appendLog("发现可疑so映射: " + line);
                        foundSuspicious = true;
                    }
                }
            }

            appendLog(foundSuspicious ? "警告：检测到Native库注入" : "未检测到Native库注入");
        } catch (Exception e) {
            appendLog("Native库检查异常: " + e);
        }
    }

    // 6. 调试状态检测
    private void checkDebuggableStatus() {
        try {
            appendLog("\n[6] 检查调试状态...");

            @SuppressLint("PrivateApi") Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Field debuggerField = activityThread.getDeclaredField("mDebugger");
            debuggerField.setAccessible(true);
            Object debugger = debuggerField.get(activityThread.getMethod("currentActivityThread").invoke(null));

            if (debugger != null) {
                appendLog("警告：检测到调试器连接");
            } else {
                appendLog("未检测到调试器");
            }

            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("TracerPid:")) {
                        String pid = line.substring(line.indexOf(":") + 1).trim();
                        if (!pid.equals("0")) {
                            appendLog("警告：检测到进程被追踪 (TracerPid: " + pid + ")");
                        } else {
                            appendLog("TracerPid: 0 (正常)");
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            appendLog("调试检查异常: " + e);
        }
    }

    // 7. 签名有效性检查
    private void checkSignatureValid() {
        try {
            appendLog("\n[7] 验证签名有效性...");

            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                    getPackageName(), PackageManager.GET_SIGNATURES);

            Signature[] signatures = packageInfo.signatures;
            if (signatures == null || signatures.length == 0) {
                appendLog("警告：无签名");
                return;
            }

            for (Signature signature : signatures) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate)
                        cf.generateCertificate(new ByteArrayInputStream(signature.toByteArray()));

                try {
                    cert.checkValidity();
                    appendLog("证书有效期验证通过: " + cert.getNotBefore() + " 至 " + cert.getNotAfter());

                    cert.verify(cert.getPublicKey());
                    appendLog("签名自验证通过");
                } catch (Exception e) {
                    appendLog("警告：签名验证失败: " + e);
                }
            }
        } catch (Exception e) {
            appendLog("签名检查异常: " + e);
        }
    }

    // 以下是原有基础检测方法
    private void checkAssets() {
        try (InputStream is = getAssets().open("测试.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
            appendLog("资源文件内容验证通过");
        } catch (IOException e) {
            appendLog("资源文件检查异常: " + e);
        }
    }

    private void printSignatureDetails() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                    getPackageName(), PackageManager.GET_SIGNATURES);

            appendLog("\n[0] 签名信息:");
            assert packageInfo.signatures != null;
            for (Signature signature : packageInfo.signatures) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate)
                        cf.generateCertificate(new ByteArrayInputStream(signature.toByteArray()));

                appendLog("签发者: " + cert.getIssuerDN());
                appendLog("SHA256: " + bytesToHex(MessageDigest.getInstance("SHA-256")
                        .digest(signature.toByteArray())));
            }
        } catch (Exception e) {
            appendLog("签名解析异常: " + e);
        }
    }

    private void verifyApkIntegrity() {
        try {
            String apkPath = getPackageCodePath();
            MessageDigest md = MessageDigest.getInstance("SHA-512");

            try (InputStream is = new FileInputStream(apkPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }

                appendLog("\nAPK完整性校验: SHA512=" + bytesToHex(md.digest()));
            }
        } catch (Exception e) {
            appendLog("完整性校验异常: " + e);
        }
    }

    private void checkProcessMaps() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/" + Process.myPid() +"/maps"))) {
            String result = reader.lines()
                    .filter(line -> line.contains(".apk") && line.contains("data/") || line.contains(".so") && line.contains("data/"))
                    .collect(Collectors.joining("\n"));
            appendLog("\n进程内存映射检查:\n" + result);
        } catch (IOException e) {
            appendLog("内存映射检查异常: " + e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = "0123456789ABCDEF".charAt(v >>> 4);
            hexChars[i * 2 + 1] = "0123456789ABCDEF".charAt(v & 0x0F);
        }
        return new String(hexChars);
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            logBuilder.append(message).append("\n");
            tvLog.setText(logBuilder.toString());
            Log.d("SecurityCheck", message);
        });
    }
}