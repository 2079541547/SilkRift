package eternal.future.silkrift;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.lang.reflect.Array;

public class APKKiller {
    public static final String TAG = "APKKiller";
    public static volatile String packageName;
    public static volatile String apkPath;
    public static volatile Object proxy;
    public static volatile Object originalPkgMgr;
    public static volatile byte[] fakeSignature;
    public static volatile PackageInfo targetPackageInfo;
    public static volatile boolean fakeApplicationInfo = true;

    public static class PackageManagerInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            Log.d(TAG, "Intercepted PackageManager call: " + methodName);

            try {
                Object original = APKKiller.getOriginalPkgMgr();
                if (original == null) {
                    Log.e(TAG, "Original PackageManager is null!");
                    return method.invoke(null, args);
                }

                Object result = method.invoke(original, args);
                String currentPackageName = APKKiller.getPackageName();
                String currentApkPath = APKKiller.getApkPath();

                if ("getPackageInfo".equals(methodName) && args != null && args.length >= 2) {
                    String pkgName = (String) args[0];
                    long flags = 0;

                    if (args[1] instanceof Integer) {
                        flags = ((Integer) args[1]).longValue();
                    } else if (args[1] instanceof Long) {
                        flags = (Long) args[1];
                    }

                    if (currentPackageName.equals(pkgName) && result != null) {
                        PackageInfo pkgInfo = (PackageInfo) result;
                        if (pkgInfo.applicationInfo != null) {
                            APKKiller.safeSetApplicationInfoPaths(pkgInfo.applicationInfo, currentApkPath);
                            APKKiller.patchApplicationInfoFields(pkgInfo.applicationInfo);
                            Log.d(TAG, "Patched PackageInfo for " + pkgName);
                        }

                        if ((flags & PackageManager.GET_SIGNATURES) != 0) {
                            APKKiller.patchSignatures(pkgInfo);
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                                (flags & PackageManager.GET_SIGNING_CERTIFICATES) != 0) {
                            APKKiller.patchSigningInfo(pkgInfo);
                        }

                        APKKiller.patchVersionInfo(pkgInfo);
                    }

                } else if ("getApplicationInfo".equals(methodName) && args != null && args.length >= 2) {
                    String pkgName = (String) args[0];
                    if (currentPackageName.equals(pkgName) && result != null) {
                        ApplicationInfo appInfo = (ApplicationInfo) result;
                        APKKiller.safeSetApplicationInfoPaths(appInfo, currentApkPath);
                        APKKiller.patchApplicationInfoFields(appInfo);
                        Log.d(TAG, "Patched ApplicationInfo for " + pkgName);
                    }
                } else if ("getInstallerPackageName".equals(methodName) && args != null && args.length >= 1) {
                    String pkgName = (String) args[0];
                    if (currentPackageName.equals(pkgName)) {
                        Log.d(TAG, "Returning fake installer package name for " + pkgName);
                        return "com.android.vending";
                    }
                }

                return result;
            } catch (InvocationTargetException e) {
                Log.e(TAG, "InvocationTargetException in PackageManager proxy", e);
            } catch (Exception e) {
                Log.e(TAG, "Exception in PackageManager proxy", e);
            }

            return proxy;
        }
    }

    public static String getPackageName() {
        return packageName;
    }

    public static String getApkPath() {
        return apkPath;
    }

    public static synchronized Object getOriginalPkgMgr() {
        return originalPkgMgr;
    }

    public static synchronized void setOriginalPkgMgr(Object pkgMgr) {
        originalPkgMgr = pkgMgr;
    }

    public static void init(Context context, String targetApkPath) {
        if (context == null || targetApkPath == null) {
            throw new IllegalArgumentException("Context and APK path cannot be null");
        }

        try {
            Log.i(TAG, "Initializing APKKiller with path: " + targetApkPath);
            packageName = context.getPackageName();
            apkPath = targetApkPath;

            targetPackageInfo = loadTargetPackageInfo(context, targetApkPath);
            if (targetPackageInfo == null) {
                Log.e(TAG, "Failed to load target package info");
            }
            Log.d(TAG, "Loaded target package info: " + targetPackageInfo);

            Object activityThread = getActivityThread();
            if (activityThread == null) {
                Log.e(TAG, "Failed to get ActivityThread instance");
            }

            fakeSignature = getApkSignatureBytes(context, targetApkPath);
            if (fakeSignature == null) {
                Log.e(TAG, "Failed to get signature from target APK");
            }
            Log.d(TAG, "Obtained signature from target APK, length: " + fakeSignature.length);

            assert activityThread != null;
            patchApplicationInfo(activityThread);
            patchLoadedApk(activityThread);
            patchAppBindData(activityThread);
            patchApplication(activityThread);
            patchPackageManager(context);

            Log.i(TAG, "APKKiller initialization completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Initialization failed", e);
            throw new RuntimeException("APKKiller initialization failed", e);
        }
    }

    public static PackageInfo loadTargetPackageInfo(Context context, String apkPath) {
        try {
            PackageManager pm = context.getPackageManager();
            int flags = PackageManager.GET_META_DATA |
                    PackageManager.GET_SHARED_LIBRARY_FILES |
                    PackageManager.GET_SIGNATURES;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                flags |= PackageManager.GET_SIGNING_CERTIFICATES;
            }

            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, flags);
            if (packageInfo != null) {
                assert packageInfo.applicationInfo != null;
                packageInfo.applicationInfo.sourceDir = apkPath;
                packageInfo.applicationInfo.publicSourceDir = apkPath;

                packageInfo.applicationInfo.splitSourceDirs = new String[]{apkPath};
                packageInfo.applicationInfo.splitPublicSourceDirs = new String[]{apkPath};

                return packageInfo;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load target package info", e);
        }
        return null;
    }

    public static byte[] getApkSignatureBytes(Context context, String apkPath) {
        if (context == null || apkPath == null) {
            return null;
        }

        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(
                    apkPath,
                    PackageManager.GET_SIGNATURES
            );

            if (packageInfo != null) {
                if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                    return packageInfo.signatures[0].toByteArray();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageInfo.signingInfo != null) {
                    Signature[] signatures = packageInfo.signingInfo.getApkContentsSigners();
                    if (signatures != null && signatures.length > 0) {
                        return signatures[0].toByteArray();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get APK signature", e);
        }
        return null;
    }

    public static void patchSignatures(PackageInfo pkgInfo) {
        if (pkgInfo == null || fakeSignature == null) {
            return;
        }

        try {
            Signature[] signatures = new Signature[1];
            signatures[0] = new Signature(fakeSignature);
            pkgInfo.signatures = signatures;
            Log.d(TAG, "Patched package signatures");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Field signingInfoField = PackageInfo.class.getDeclaredField("signingInfo");
                    signingInfoField.setAccessible(true);
                    Object signingInfo = signingInfoField.get(pkgInfo);

                    if (signingInfo != null) {
                        patchSigningInfoInternal(signingInfo);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to patch signingInfo", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch signatures", e);
        }
    }

    public static void patchSigningInfo(PackageInfo pkgInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || pkgInfo == null || fakeSignature == null) {
            return;
        }

        try {
            Object signingInfo = pkgInfo.signingInfo;
            if (signingInfo == null) {
                Class<?> signingInfoClass = Class.forName("android.content.pm.SigningInfo");
                Constructor<?> constructor = signingInfoClass.getConstructor(Signature[].class);

                Signature[] signatures = new Signature[1];
                signatures[0] = new Signature(fakeSignature);
                signingInfo = constructor.newInstance((Object) signatures);

                Field signingInfoField = PackageInfo.class.getDeclaredField("signingInfo");
                signingInfoField.setAccessible(true);
                signingInfoField.set(pkgInfo, signingInfo);
            } else {
                patchSigningInfoInternal(signingInfo);
            }
            Log.d(TAG, "Patched signingInfo");
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch signingInfo", e);
        }
    }

    public static void patchSigningInfoInternal(Object signingInfo) throws Exception {
        if (signingInfo == null || fakeSignature == null) {
            return;
        }

        Signature[] signatures = new Signature[1];
        signatures[0] = new Signature(fakeSignature);

        Method setSignatures = signingInfo.getClass().getMethod("setSignatures", Signature[].class);
        setSignatures.invoke(signingInfo, (Object) signatures);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Class<?> signatureClass = Class.forName("android.content.pm.Signature");
                Object[] signatureArray = (Object[]) Array.newInstance(signatureClass, 1);
                signatureArray[0] = signatures[0];

                Method computeSignatures = signingInfo.getClass().getDeclaredMethod("computeSignatures");
                computeSignatures.setAccessible(true);
                computeSignatures.invoke(signingInfo);
            } catch (Exception e) {
                Log.w(TAG, "Failed to compute signatures", e);
            }
        }
    }

    public static void patchVersionInfo(PackageInfo pkgInfo) {
        if (pkgInfo == null || targetPackageInfo == null) {
            return;
        }

        try {
            Log.d(TAG, "Patching version info from target package");
            pkgInfo.versionCode = targetPackageInfo.versionCode;
            pkgInfo.versionName = targetPackageInfo.versionName;

            Log.d(TAG, "Patched version info: versionCode=" + pkgInfo.versionCode +
                    ", versionName=" + pkgInfo.versionName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch version info", e);
        }
    }

    public static void patchApplicationInfoFields(ApplicationInfo appInfo) {
        if (!fakeApplicationInfo) return;

        if (appInfo == null || targetPackageInfo == null || targetPackageInfo.applicationInfo == null) {
            return;
        }

        try {
            if (targetPackageInfo == null || targetPackageInfo.applicationInfo == null) {
                return;
            }

            ApplicationInfo targetAppInfo = targetPackageInfo.applicationInfo;
            assert targetAppInfo != null;

            String[] fieldsToPatch = {
                    "name", "className", "uid",
                    // "sharedLibraryFiles", "nativeLibraryDir",
                    "flags",
                    "theme", "manageSpaceActivityName", "descriptionRes", "uiOptions",
                    "icon",
                    "metaData",
                    "deviceProtectedDataDir", "backupAgentName", "banner",
                    "permission",
                    "labelRes", "taskAffinity",
                    "minSdkVersion"
            };

            for (String field : fieldsToPatch) {
                try {
                    if (getFieldValue(appInfo, field) != null) {
                        Object targetValue = getFieldValue(targetAppInfo, field);
                        Log.d(TAG, "Set the fields: " + field);
                        setFieldValue(appInfo, field, targetValue);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to patch field: " + field, e);
                }
            }


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setFieldValue(appInfo, "category", getFieldValue(targetAppInfo, "category"));
                setFieldValue(appInfo, "splitNames", getFieldValue(targetAppInfo, "splitNames"));
                setFieldValue(appInfo, "storageUuid", getFieldValue(targetAppInfo, "storageUuid"));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                setFieldValue(appInfo, "isArchived", getFieldValue(targetAppInfo, "isArchived"));
            }


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setFieldValue(appInfo, "appComponentFactory", getFieldValue(targetAppInfo, "appComponentFactory"));
            }


            Log.d(TAG, "Patched ApplicationInfo fields: name=" + appInfo.name +
                    ", className=" + appInfo.className + ", uid=" + appInfo.uid +
                    ", flags=" + appInfo.flags + ", debuggable=" + ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0));
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch ApplicationInfo fields", e);
        }
    }

    public static Object getActivityThread() throws Exception {
        Log.d(TAG, "Getting ActivityThread instance");
        @SuppressLint("PrivateApi") Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method currentActivityThreadMethod = activityThreadClass.getMethod("currentActivityThread");
        return currentActivityThreadMethod.invoke(null);
    }

    public static void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        try {
            Field field = findFieldRecursively(obj.getClass(), fieldName);
            field.setAccessible(true);

            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (Exception e) {
                Log.w(TAG, "Failed to remove final modifier for field: " + fieldName, e);
            }

            field.set(obj, value);
        } catch (Exception e) {
            Log.w(TAG, "Field modification failed: " + e);
        }
    }

    public static Object getFieldValue(Object obj, String fieldName) throws Exception {
        Field field = findFieldRecursively(obj.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }

    public static void safeSetApplicationInfoPaths(ApplicationInfo appInfo, String newPath) {
        try {
            setFieldValue(appInfo, "sourceDir", newPath);
            setFieldValue(appInfo, "publicSourceDir", newPath);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    setFieldValue(appInfo, "mSplitSourceDirs", new String[]{newPath});
                    setFieldValue(appInfo, "mSplitPublicSourceDirs", new String[]{newPath});
                } catch (Exception e) {
                    Log.w(TAG, "Failed to set split paths", e);
                }
            }

            try {
                setFieldValue(appInfo, "resourceDirs", new String[]{newPath});
                setFieldValue(appInfo, "publicResourceDirs", new String[]{newPath});
            } catch (Exception e) {
                Log.w(TAG, "Failed to set resource paths", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical: Failed to set application info paths", e);
        }
    }

    public static void patchApplicationInfo(Object activityThread) throws Exception {
        Log.d(TAG, "Patching ApplicationInfo");
        Class<?> activityThreadClass = activityThread.getClass();
        Field mBoundApplicationField = getField(activityThreadClass, "mBoundApplication");
        Object mBoundApplication = mBoundApplicationField.get(activityThread);

        if (mBoundApplication != null) {
            @SuppressLint("PrivateApi") Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Field appInfoField = getField(appBindDataClass, "appInfo");
            ApplicationInfo appInfo = (ApplicationInfo) appInfoField.get(mBoundApplication);

            if (appInfo != null) {
                safeSetApplicationInfoPaths(appInfo, apkPath);
                patchApplicationInfoFields(appInfo);
                Log.d(TAG, "Patched ApplicationInfo fields");
            }
        }
    }

    public static void patchLoadedApk(Object activityThread) throws Exception {
        Log.d(TAG, "Patching LoadedApk");
        Class<?> activityThreadClass = activityThread.getClass();
        Field mPackagesField = getField(activityThreadClass, "mPackages");
        ArrayMap<String, WeakReference<?>> mPackages = (ArrayMap<String, WeakReference<?>>) mPackagesField.get(activityThread);

        if (mPackages != null) {
            for (int i = 0; i < mPackages.size(); i++) {
                WeakReference<?> ref = mPackages.valueAt(i);
                Object loadedApk = ref.get();

                if (loadedApk != null) {
                    setFieldValue(loadedApk, "mAppDir", apkPath);
                    setFieldValue(loadedApk, "mResDir", apkPath);

                    ApplicationInfo appInfo = (ApplicationInfo) getFieldValue(loadedApk, "mApplicationInfo");
                    if (appInfo != null) {
                        safeSetApplicationInfoPaths(appInfo, apkPath);
                        patchApplicationInfoFields(appInfo);
                    }
                    Log.d(TAG, "Patched LoadedApk #" + i);
                }
            }
        }
    }

    public static void patchAppBindData(Object activityThread) throws Exception {
        Log.d(TAG, "Patching AppBindData");
        Class<?> activityThreadClass = activityThread.getClass();
        Field mBoundApplicationField = getField(activityThreadClass, "mBoundApplication");
        Object mBoundApplication = mBoundApplicationField.get(activityThread);

        if (mBoundApplication != null) {
            @SuppressLint("PrivateApi") Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Field infoField = getField(appBindDataClass, "info");
            Object loadedApk = infoField.get(mBoundApplication);

            if (loadedApk != null) {
                ApplicationInfo appInfo = (ApplicationInfo) getFieldValue(loadedApk, "mApplicationInfo");
                if (appInfo != null) {
                    safeSetApplicationInfoPaths(appInfo, apkPath);
                    patchApplicationInfoFields(appInfo);
                }
            }
        }
    }

    public static void patchApplication(Object activityThread) throws Exception {
        Log.d(TAG, "Patching Application");
        Class<?> activityThreadClass = activityThread.getClass();
        Field mInitialApplicationField = getField(activityThreadClass, "mInitialApplication");
        Object application = mInitialApplicationField.get(activityThread);

        if (application != null) {
            Class<?> applicationClass = application.getClass();
            Field mLoadedApkField = getField(applicationClass, "mLoadedApk");
            Object loadedApk = mLoadedApkField.get(application);

            if (loadedApk != null) {
                ApplicationInfo appInfo = (ApplicationInfo) getFieldValue(loadedApk, "mApplicationInfo");
                if (appInfo != null) {
                    safeSetApplicationInfoPaths(appInfo, apkPath);
                    patchApplicationInfoFields(appInfo);
                }
            }
        }
    }

    public static void patchPackageManager(Context context) throws Exception {
        Log.d(TAG, "Patching PackageManager");
        @SuppressLint("PrivateApi") Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Field sPackageManagerField = getField(activityThreadClass, "sPackageManager");

        Object original = sPackageManagerField.get(null);
        setOriginalPkgMgr(original);

        @SuppressLint("PrivateApi") Class<?> iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager");
        proxy = Proxy.newProxyInstance(
                iPackageManagerInterface.getClassLoader(),
                new Class<?>[]{iPackageManagerInterface},
                new PackageManagerInvocationHandler()
        );

        sPackageManagerField.set(null, proxy);

        PackageManager pm = context.getPackageManager();
        @SuppressLint("PrivateApi") Class<?> applicationPackageManagerClass = Class.forName("android.app.ApplicationPackageManager");
        Field mPMField = getField(applicationPackageManagerClass, "mPM");
        mPMField.set(pm, proxy);

        Log.d(TAG, "PackageManager patching completed");
    }

    public static Field getField(Class<?> clazz, String fieldName) throws Exception {
        return findFieldRecursively(clazz, fieldName);
    }

    public static Field findFieldRecursively(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        assert clazz != null;
        throw new NoSuchFieldException(fieldName + " not found in " + clazz.getName());
    }

    public static void setFakeApplicationInfo(boolean enable) {
        fakeApplicationInfo = enable;
    }
}