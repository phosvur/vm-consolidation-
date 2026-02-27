package com.sphat1.consolidation;

import org.cloudbus.cloudsim.UtilizationModel;

public class UtilizationModelDynamic implements UtilizationModel {

    private final double baseUtilization;   // centre of oscillation (0.0–1.0)
    private final double amplitude;         // how far it swings either side
    private final double frequency;         // oscillations per second (e.g. 1/600)
    private final double phaseShift;        // radians, gives each VM a unique curve
    private final double peakTime;          // for PEAK scenario: centre of spike (seconds)
    private final double peakAmplitude;     // extra utilization added during spike
    private final double peakWidth;         // how wide the spike is in seconds (std dev)
    private final boolean usePeakSpike;     // whether to add the Gaussian spike

    // General constructor (STEADY and VARIABLE scenarios)
    public UtilizationModelDynamic(double baseUtilization, double amplitude,
                                    double frequency, double phaseShift) {
        this.baseUtilization = baseUtilization;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.phaseShift = phaseShift;
        this.peakTime = 0;
        this.peakAmplitude = 0;
        this.peakWidth = 1;
        this.usePeakSpike = false;
    }

    // PEAK scenario constructor — adds a Gaussian spike on top of the base sine
    public UtilizationModelDynamic(double baseUtilization, double amplitude,
                                    double frequency, double phaseShift,
                                    double peakTime, double peakAmplitude,
                                    double peakWidth) {
        this.baseUtilization = baseUtilization;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.phaseShift = phaseShift;
        this.peakTime = peakTime;
        this.peakAmplitude = peakAmplitude;
        this.peakWidth = peakWidth;
        this.usePeakSpike = true;
    }

    @Override
    public double getUtilization(double time) {
        // Base sine wave
        double sineComponent = baseUtilization
            + amplitude * Math.sin(2 * Math.PI * frequency * time + phaseShift);

        // Optional Gaussian spike for PEAK scenario
        double spike = 0.0;
        if (usePeakSpike) {
            double exponent = -Math.pow(time - peakTime, 2)
                              / (2 * Math.pow(peakWidth, 2));
            spike = peakAmplitude * Math.exp(exponent);
        }

        // Clamp to [0.0, 1.0] — utilization can't exceed 100% or go negative
        return Math.max(0.0, Math.min(1.0, sineComponent + spike));
    }
}