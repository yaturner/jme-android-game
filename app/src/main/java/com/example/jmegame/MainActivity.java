package com.example.jmegame;

import android.os.Bundle;
import com.jme3.app.AndroidHarness;

public class MainActivity extends AndroidHarness {

    public MainActivity() {
        // The fully-qualified name of the jME Application class to run
        appClass = CubeGame.class.getName();

        // Exit on GL error (set false for production)
        exitOnGLError = false;

        // Screen orientation: 1 = landscape, 2 = portrait
        screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
