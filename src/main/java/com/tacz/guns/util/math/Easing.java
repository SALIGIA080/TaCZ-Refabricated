package com.tacz.guns.util.math;

public class Easing {
    public static double easeOutCubic(double x) {
        return 1 - Math.pow(1 - x, 3);
    }

    public static double easeInExpo(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        return x >= 1.0 ? 1.0 : Math.pow(2.0, 10.0 * x - 10.0);
    }
}
