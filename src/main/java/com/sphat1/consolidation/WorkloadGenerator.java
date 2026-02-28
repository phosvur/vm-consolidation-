package com.sphat1.consolidation;

import org.cloudbus.cloudsim.UtilizationModel;
import java.util.Random;

public class WorkloadGenerator {

    public enum Scenario { STEADY, PEAK, VARIABLE }

    public static UtilizationModel createUtilizationModel(Scenario scenario, int vmIndex, Random rand) {
        switch (scenario) {
            case STEADY: {
                double base      = 0.45; 
                double amplitude = 0.02; 
                double frequency = 1.0 / 600.0;
                double phase     = rand.nextDouble() * 2 * Math.PI;
                return new UtilizationModelDynamic(base, amplitude, frequency, phase);
            }

            case PEAK: {
                double base      = 0.15; 
                double amplitude = 0.05;
                double frequency = 1.0 / 800.0;
                double phase     = rand.nextDouble() * 2 * Math.PI;
                double peakTime  = 400.0;
                double peakAmp   = 0.70; 
                double peakWidth = 100.0;
                return new UtilizationModelDynamic(base, amplitude, frequency, phase,
                                                 peakTime, peakAmp, peakWidth);
            }

            case VARIABLE: {
                double base      = 0.30;
                double amplitude = 0.25; 
                double frequency = 1.0 / 300.0;
                double phase     = rand.nextDouble() * 2 * Math.PI;
                return new UtilizationModelDynamic(base, amplitude, frequency, phase);
            }
            
            // This is the fix for your error:
            default:
                throw new IllegalArgumentException("Unexpected scenario: " + scenario);
        }
    }
}