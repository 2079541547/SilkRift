package eternal.future.silkrift;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Core {

    public static Boolean initialize = false;
    private static final Map<String, String> ARCH_TO_LIB = new HashMap<>();

    static {
        ARCH_TO_LIB.put("arm", "armeabi-v7a");
        ARCH_TO_LIB.put("arm64", "arm64-v8a");
        ARCH_TO_LIB.put("x86", "x86");
        ARCH_TO_LIB.put("x86_64", "x86_64");
    }


    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static void init() throws Exception {

        initialize = false;

        String arch = getRuntimeArch();
        String libDir = ARCH_TO_LIB.get(arch);
        if (libDir == null) {
            throw new UnsupportedOperationException("Unsupported architecture: " + arch);
        }

        String soAssetPath = "assets/silkrift/so/" + libDir + "/libsilkrift.so";
        File soFile = extractAssetToTemp(soAssetPath, "libsilkrift", ".so");

        System.loadLibrary("silkrift");
        // System.load(soFile.getAbsolutePath());

        initialize = true;
    }

    private static String getRuntimeArch() throws Exception {
        Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
        Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
        Method vmInstructionSet = vmRuntime.getDeclaredMethod("vmInstructionSet");

        getRuntime.setAccessible(true);
        vmInstructionSet.setAccessible(true);

        Object runtime = getRuntime.invoke(null);
        return (String) vmInstructionSet.invoke(runtime);
    }

    public static File extractAssetToTemp(String assetPath, String prefix, String suffix) throws IOException {
        File r = createTempFile(prefix, suffix);
        if (r.exists()) return r;
        try (InputStream in = Objects.requireNonNull(Core.class.getClassLoader()).getResourceAsStream(assetPath);
             ReadableByteChannel src = Channels.newChannel(Objects.requireNonNull(in, "Asset not found: " + assetPath));
             FileChannel dest = new FileOutputStream(r).getChannel()) {

            dest.transferFrom(src, 0, Long.MAX_VALUE);
        }
        return r;
    }

    private static File createTempFile(String prefix, String suffix) {
        return new File(System.getProperty("java.io.tmpdir"), prefix + suffix);
    }


    public static Context createAppContext() {
        try {
            @SuppressLint("PrivateApi") Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            @SuppressLint("PrivateApi") Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);

            @SuppressLint("PrivateApi") Field mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication");
            mBoundApplicationField.setAccessible(true);
            Object mBoundApplication = mBoundApplicationField.get(activityThread);

            @SuppressLint("PrivateApi") Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            @SuppressLint("PrivateApi") Field infoField = appBindDataClass.getDeclaredField("info");
            infoField.setAccessible(true);
            Object loadedApk = infoField.get(mBoundApplication);

            @SuppressLint("PrivateApi") Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
            assert loadedApk != null;
            @SuppressLint("PrivateApi") Method createAppContextMethod = contextImplClass.getDeclaredMethod(
                    "createAppContext",
                    activityThreadClass,
                    loadedApk.getClass()
            );
            createAppContextMethod.setAccessible(true);

            Object context = createAppContextMethod.invoke(null, activityThread, loadedApk);
            return (Context) context;
        } catch (Exception e) {
            Log.e("Core", e.toString());
            return null;
        }
    }

    public static native void StealthCrashBypass_Install();
}