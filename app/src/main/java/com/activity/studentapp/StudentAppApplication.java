package com.activity.studentapp;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

public class StudentAppApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Force light mode regardless of system setting
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}