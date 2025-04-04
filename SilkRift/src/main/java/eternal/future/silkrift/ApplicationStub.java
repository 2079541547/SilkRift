package eternal.future.silkrift;

import android.app.Application;

public class ApplicationStub extends Application {
    static {
        try {
            Core.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Core.StealthCrashBypass_Install();
        MapsGhost.init();
        MapsGhost.add_entry(IORedirects.repPath);
        MapsGhost.add_entry("libsilkrift.so");
        MapsGhost.add_entry("original.apk");
        IORedirects.RedirectAPK();
        SignatureFaker.init(Core.createAppContext(), SignatureFaker.getApkSignatureBytes(Core.createAppContext(), IORedirects.repPath));
    }
}
