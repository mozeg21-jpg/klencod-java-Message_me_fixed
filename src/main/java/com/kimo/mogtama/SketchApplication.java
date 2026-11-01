package com.kimo.mogtama;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class SketchApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
    }
}
