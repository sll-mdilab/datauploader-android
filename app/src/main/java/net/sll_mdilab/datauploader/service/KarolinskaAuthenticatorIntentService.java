package net.sll_mdilab.datauploader.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import net.sll_mdilab.datauploader.auth.KarolinskaAuthenticator;

    public class KarolinskaAuthenticatorIntentService extends Service {

    private KarolinskaAuthenticator authenticator;

    @Override
    public void onCreate() {
        authenticator = new KarolinskaAuthenticator(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return authenticator.getIBinder();
    }
}
