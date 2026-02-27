package com.sphat1.consolidation;

import org.cloudbus.cloudsim.UtilizationModel;
import java.util.Random;

public class WorkloadGenerator {
    public enum Scenario { STEADY, PEAK, VARIABLE }

    public static UtilizationModel get(Scenario s, long seed) {
        return switch (s) {
            case STEADY   -> time -> 0.45 + 0.10 * Math.sin(time / 100.0);
            case PEAK     -> time -> {
                double c = time % 500;
                return (c > 200 && c < 350) ? 0.85 : 0.30;
            };
            case VARIABLE -> new UtilizationModel() {
                final Random r = new Random(seed);
                public double getUtilization(double time) {
                    double base = 0.4 + 0.3 * r.nextDouble();
                    return r.nextDouble() < 0.15
                        ? Math.min(base + 0.4, 1.0) : base;
                }
            };
        };
    }
}