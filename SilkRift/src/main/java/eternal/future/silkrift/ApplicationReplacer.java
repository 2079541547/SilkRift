package eternal.future.silkrift;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.ArrayMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ApplicationReplacer {

    private final Context context;

    public ApplicationReplacer(Context context) {
        this.context = context;
    }

    /**
     * 从 APK 文件中解析 Application 类名
     * @param apkPath APK 文件路径
     * @return Application 类全名（如 "com.example.MyApp"）
     */
    public String parseApplicationClassName(String apkPath) {
        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA);

        if (packageInfo == null || packageInfo.applicationInfo == null) {
            throw new RuntimeException("Invalid APK or missing AndroidManifest: " + apkPath);
        }

        // 从 AndroidManifest.xml 读取 android:name
        String className = packageInfo.applicationInfo.className;
        return className != null ? className : "android.app.Application"; // 默认值
    }


    /**
     * 替换当前进程的 Application
     * @param apkPath 目标 APK 路径
     */
    public void replaceApplication(String apkPath) throws Throwable {
        String targetClassName = parseApplicationClassName(apkPath);

        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Field sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
        sCurrentActivityThreadField.setAccessible(true);
        Object activityThread = sCurrentActivityThreadField.get(null);

        // 4. 获取 mBoundApplication (AppBindData)
        Field mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication");
        mBoundApplicationField.setAccessible(true);
        Object appBindData = mBoundApplicationField.get(activityThread);

        // 5. 获取 LoadedApk (info)
        Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
        Field infoField = appBindDataClass.getDeclaredField("info");
        infoField.setAccessible(true);
        Object loadedApk = infoField.get(appBindData);

        // 6. 清空旧 Application 引用
        Field mApplicationField = loadedApk.getClass().getDeclaredField("mApplication");
        mApplicationField.setAccessible(true);
        mApplicationField.set(loadedApk, null);

        // 7. 从 mAllApplications 移除旧实例
        Field mAllApplicationsField = activityThreadClass.getDeclaredField("mAllApplications");
        mAllApplicationsField.setAccessible(true);
        ArrayList<Application> appList = (ArrayList<Application>) mAllApplicationsField.get(activityThread);
        Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
        mInitialApplicationField.setAccessible(true);
        Application oldApp = (Application) mInitialApplicationField.get(activityThread);
        appList.remove(oldApp);

        // 8. 修改 ApplicationInfo 的类名
        Field appInfoField = loadedApk.getClass().getDeclaredField("mApplicationInfo");
        appInfoField.setAccessible(true);
        ApplicationInfo appInfo = (ApplicationInfo) appInfoField.get(loadedApk);
        appInfo.className = targetClassName;

        // 9. 同步修改 AppBindData 中的 appInfo
        Field bindDataAppInfoField = appBindDataClass.getDeclaredField("appInfo");
        bindDataAppInfoField.setAccessible(true);
        ((ApplicationInfo) bindDataAppInfoField.get(appBindData)).className = targetClassName;

        // 10. 创建新 Application 实例
        Method makeApplicationMethod = loadedApk.getClass().getDeclaredMethod(
                "makeApplication", boolean.class, Class.forName("android.app.Instrumentation")
        );
        makeApplicationMethod.setAccessible(true);
        Application newApp = (Application) makeApplicationMethod.invoke(loadedApk, false, null);

        // 11. 更新系统引用
        mInitialApplicationField.set(activityThread, newApp);
        mApplicationField.set(loadedApk, newApp);
        appList.add(newApp);

        // 12. 修复 ContentProvider 的 Context
        fixContentProvidersContext(activityThread, newApp);
    }

    /**
     * 修复 ContentProvider 持有的 Context 引用
     */
    private void fixContentProvidersContext(Object activityThread, Application newApp) throws Exception {
        Field mProviderMapField = activityThread.getClass().getDeclaredField("mProviderMap");
        mProviderMapField.setAccessible(true);
        ArrayMap<?, ?> providerMap = (ArrayMap<?, ?>) mProviderMapField.get(activityThread);

        for (Object providerRecord : providerMap.values()) {
            if (providerRecord == null) continue;

            Field mLocalProviderField = providerRecord.getClass().getDeclaredField("mLocalProvider");
            mLocalProviderField.setAccessible(true);
            Object localProvider = mLocalProviderField.get(providerRecord);

            if (localProvider != null) {
                Field mContextField = localProvider.getClass().getDeclaredField("mContext");
                mContextField.setAccessible(true);
                mContextField.set(localProvider, newApp);
            }
        }
    }
}