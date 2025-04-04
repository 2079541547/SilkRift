package eternal.future.silkrift;

import java.io.IOException;
import java.util.Objects;

public class IORedirects {

    public static String repPath;
    public static String apkPath;

    static {
        try {
            if (!Core.initialize) Core.init();
            apkPath = Objects.requireNonNull(Core.createAppContext()).getPackageCodePath();
            repPath = String.valueOf(Core.extractAssetToTemp("assets/silkrift/original.apk", "original", ".apk"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void RedirectAPK() {
        init(false, false, true);
        add_entry(Objects.requireNonNull(Core.createAppContext()).getPackageCodePath(), repPath);
    }
    public static native void add_entry(String from, String to);
    public static native void init(boolean open, boolean openat, boolean __openat);
}
