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
        IORedirects.RedirectAPK();
        MapsGhost.init();
        MapsGhost.add_entry(IORedirects.repPath);
        MapsGhost.add_entry("libsilkrift.so");
        SignatureFaker.init(Core.createAppContext(), SignatureFaker.getApkSignatureBytes(Core.createAppContext(), IORedirects.repPath));
    }
}
