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
        IORedirects.RedirectAPK();
        MapsGhost.add_entry("original.apk");
        MapsGhost.add_entry("libsilkrift.so");
        APKKiller.init(Core.createAppContext(), IORedirects.repPath);
    }
}
