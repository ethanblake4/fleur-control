package org.fleurcontrol.controllers.util;

import android.content.Context;

import com.google.ar.core.Session;

public class BackgroundDrawer {
    //the background / camera display
    public BackgroundRenderer background = new BackgroundRenderer();

    private final Session arCoreSession;

    public BackgroundDrawer(Session mArCoreSession) {
        this.arCoreSession = mArCoreSession;
    }


    public void prepare(Context context) {

        background.createOnGlThread(/*context=*/context);

        arCoreSession.setCameraTextureName(background.getTextureId());
    }

    public void onDraw(ARCanvas arCanvas) {
        background.draw(arCanvas.getArcoreFrame());
    }
}