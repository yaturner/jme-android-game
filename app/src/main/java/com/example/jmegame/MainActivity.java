package com.example.jmegame;

import com.jme3.app.AndroidHarness;

public class MainActivity extends AndroidHarness {

    public MainActivity() {
        appClass = CubeGame.class.getName();
    }
}
