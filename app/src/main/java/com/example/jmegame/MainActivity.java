package com.example.jmegame;

import android.os.Bundle;
import com.jme3.app.AndroidHarness;

public class MainActivity extends AndroidHarness {

    public MainActivity() {
        appClass = CubeGame.class.getName();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
