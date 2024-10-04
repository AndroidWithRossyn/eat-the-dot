package com.buildbox;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.HashMap;

public interface CustomIntegrator extends Integrator
{
    void loadingDidComplete();

    void screenOnEnter(String screenName);

    void screenOnExit(String screenName);

    void sceneOnEnter(String sceneName);

    void sceneOnExit(String sceneName);

    void buttonActivated(String buttonName);

    boolean buttonVisible(String buttonName);
}
