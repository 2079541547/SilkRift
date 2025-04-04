package eternal.future.silkrift;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class SignatureFaker {
    private static final String TAG = "SignatureFaker";
    private static boolean isHooked = false;
    private static byte[] fakeSignature;

    public static synchronized void init(Context context, byte[] signature) {
        if (isHooked) {
            Log.d(TAG, "Already hooked, skipping");
            return;
        }

        if (context == null) {
            Log.e(TAG, "Context cannot be null");
            return;
        }

        if (signature == null || signature.length == 0) {
            Log.e(TAG, "Invalid signature provided");
            return;
        }

        fakeSignature = Arrays.copyOf(signature, signature.length);

        try {
            Object activityThread = getActivityThread();
            if (activityThread == null) {
                throw new RuntimeException("Failed to get ActivityThread");
            }

            Object originalPm = getIPackageManager(activityThread);
            if (originalPm == null) {
                throw new RuntimeException("Failed to get IPackageManager");
            }

            Object proxy = createProxy(originalPm);

            replaceService(activityThread, proxy);

            replaceAppPackageManager(context, proxy);

            isHooked = true;
            Log.d(TAG, "Signature spoofing activated successfully");
        } catch (Throwable e) {
            Log.e(TAG, "Hook failed", e);
        }
    }

    private static Object getActivityThread() throws Exception {
        @SuppressLint("PrivateApi")
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method currentActivityThread = activityThreadClass.getMethod("currentActivityThread");
        currentActivityThread.setAccessible(true);
        return currentActivityThread.invoke(null);
    }

    private static Object getIPackageManager(Object activityThread) throws Exception {
        Field sPackageManagerField = activityThread.getClass().getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        return sPackageManagerField.get(activityThread);
    }

    private static Object createProxy(Object originalPm) throws Exception {
        @SuppressLint("PrivateApi")
        Class<?> iPmInterface = Class.forName("android.content.pm.IPackageManager");

        return Proxy.newProxyInstance(
                iPmInterface.getClassLoader(),
                new Class[]{iPmInterface},
                new PackageManagerHook(originalPm)
        );
    }

    private static void replaceService(Object activityThread, Object proxy) throws Exception {
        // 替换ActivityThread中的sPackageManager
        Field sPackageManagerField = activityThread.getClass().getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        sPackageManagerField.set(activityThread, proxy);

        // 替换ServiceManager中的缓存
        @SuppressLint("PrivateApi")
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        @SuppressLint("PrivateApi") Field cacheField = serviceManagerClass.getDeclaredField("sCache");
        cacheField.setAccessible(true);

        @SuppressWarnings("unchecked")
        android.util.ArrayMap<String, Object> cache =
                (android.util.ArrayMap<String, Object>) cacheField.get(null);

        if (cache != null) {
            synchronized (cache) {
                cache.put("package", proxy);
            }
        }
    }

    private static void replaceAppPackageManager(Context context, Object proxy) throws Exception {
        PackageManager pm = context.getPackageManager();
        @SuppressLint("PrivateApi") Field mPmField = pm.getClass().getDeclaredField("mPM");
        mPmField.setAccessible(true);
        mPmField.set(pm, proxy);
    }

    private static class PackageManagerHook implements InvocationHandler {
        private final Object original;

        PackageManagerHook(Object original) {
            this.original = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                if (method == null) {
                    return null;
                }

                String methodName = method.getName();

                if ("getPackageInfo".equals(methodName) && args != null && args.length >= 2) {
                    return handleGetPackageInfo(method, args);
                }

                if ("getPackageInfoAsUser".equals(methodName) && args != null && args.length >= 3) {
                    return handleGetPackageInfo(method, args);
                }

                return method.invoke(original, args);
            } catch (Throwable e) {
                Log.w(TAG, "Invocation failed for method: " + method.getName(), e);
                throw e;
            }
        }

        private Object handleGetPackageInfo(Method method, Object[] args) throws Throwable {
            String pkgName = (args[0] instanceof String) ? (String) args[0] : null;
            int flags = getFlags(args[1]);

            Object result = method.invoke(original, args);
            if (result == null) {
                return null;
            }

            if ((flags & PackageManager.GET_SIGNATURES) != 0) {
                Log.d(TAG, "Intercepted signature request for package: " + pkgName);
                return createFakePackageInfo(result);
            }

            return result;
        }

        private int getFlags(Object flagsObj) {
            if (flagsObj instanceof Integer) {
                return (Integer) flagsObj;
            } else if (flagsObj instanceof Long) {
                return ((Long) flagsObj).intValue();
            }
            return 0;
        }

        private PackageInfo createFakePackageInfo(Object originalResult) throws Exception {
            PackageInfo pi = (PackageInfo) originalResult;

            Class<?> signatureClass = Class.forName("android.content.pm.Signature");
            Object signature = signatureClass.getConstructor(byte[].class)
                    .newInstance((Object) fakeSignature);

            Object[] signatures = (Object[]) Array.newInstance(signatureClass, 1);
            signatures[0] = signature;

            Field signaturesField = PackageInfo.class.getDeclaredField("signatures");
            signaturesField.setAccessible(true);
            signaturesField.set(pi, signatures);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    Class<?> signingInfoClass = Class.forName("android.content.pm.SigningInfo");
                    Object signingInfo = signingInfoClass.getConstructor(signatureClass)
                            .newInstance(signature);

                    Field signingInfoField = PackageInfo.class.getDeclaredField("signingInfo");
                    signingInfoField.setAccessible(true);
                    signingInfoField.set(pi, signingInfo);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to handle signingInfo", e);
                }
            }

            return pi;
        }
    }

    public static byte[] getApkSignatureBytes(Context context, String apkPath) {
        if (context == null || apkPath == null) {
            return null;
        }

        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES);

            if (packageInfo != null && packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                return packageInfo.signatures[0].toByteArray();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get APK signature", e);
        }
        return null;
    }

    public static byte[] getAppSignatureBytes(Context context) {
        if (context == null) {
            return null;
        }

        try {
            String packageName = context.getPackageName();
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);

            if (packageInfo != null && packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                return packageInfo.signatures[0].toByteArray();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get app signature", e);
        }
        return null;
    }
}