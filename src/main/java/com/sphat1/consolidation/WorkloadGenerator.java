package com.sphat1.consolidation;

import org.cloudbus.cloudsim.UtilizationModel;
import java.util.Random;

public class WorkloadGenerator {

    public enum Scenario { STEADY, PEAK, VARIABLE }

    public static UtilizationModel createUtilizationModel(
            Scenario scenario, int vmIndex, Random rand) {

        switch (scenario) {

            case STEADY: {
                double base      = 0.35;
                double amplitude = 0.10;
                double frequency = 1.0 / 600.0;
                double phase     = rand.nextDouble() * 2 * Math.PI;
                return new UtilizationModelDynamic(base, amplitude, frequency, phase);
            }

            case PEAK: {
                double base      = 0.15 + rand.nextDouble() * 0.10;
                double amplitude = 0.05;
                double frequency = 1.0 / 800.0;
                double phase     = rand.nextDouble() * 2 * Math.PI;
                boolean spikes   = rand.nextDouble() < 0.40;
                double peakTime  = 300.0;
                double peakAmp   = spikes ? 0.70 : 0.0;
                double peakWidth = 80.0;
                return new UtilizationModelDynamic(
                    base, amplitude, frequency, phase,
                    peakTime, peakAmp, peakWidth);
            }

            case VARIABLE: {
                double base      = 0.10 + rand.nextDouble() * 0.60;
                double amplitude = 0.05 + rand.nextDouble() * 0.20;
                double frequency = (0.5 + rand.nextDouble()) / 600.0;
                double phase     = rand.nextDouble() * 2 * Math.PI;
                return new UtilizationModelDynamic(base, amplitude, frequency, phase);
            }

            default:
                throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }
}