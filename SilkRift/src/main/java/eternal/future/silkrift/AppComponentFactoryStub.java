package eternal.future.silkrift;

import android.annotation.SuppressLint;
import android.app.AppComponentFactory;

@SuppressLint("NewApi")
public class AppComponentFactoryStub extends AppComponentFactory {
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
