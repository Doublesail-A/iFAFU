package com.nineoldandroids.view;

import android.view.View;

/** Compatibility facade for the legacy NineOldAndroids calls in DragLayout. */
public final class ViewHelper {
    private ViewHelper() {
    }

    public static void setScaleX(View view, float value) {
        view.setScaleX(value);
    }

    public static void setScaleY(View view, float value) {
        view.setScaleY(value);
    }

    public static void setTranslationX(View view, float value) {
        view.setTranslationX(value);
    }

    public static void setAlpha(View view, float value) {
        view.setAlpha(value);
    }
}
