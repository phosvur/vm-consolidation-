package com.sphat1.consolidation;
//import com.sphat1.consolidation.ConsolidatingDatacenter;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
import java.util.Arrays;
import java.util.*;


public class SimulationRunner {

    public static void main(String[] args) throws Exception {
        for (WorkloadGenerator.Scenario s : WorkloadGenerator.Scenario.values()) {
            System.out.println("\n========== Scenario: " + s + " ==========");
            runScenario(s);
        }
    }

    static void runScenario(WorkloadGenerator.Scenario scenario) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        List<PowerHost> hosts = DataCenterConfig.createHosts();

        HeuristicConsolidationPolicy policy =
            new HeuristicConsolidationPolicy(hosts,
                new SelectionPolicyMinimumUtilization());

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", hosts, 0, 0, 0, 0, 0);

        ConsolidatingDatacenter dc = new ConsolidatingDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 300);
        dc.setDisableMigrations(false);

        DatacenterBroker broker = new DatacenterBroker("Broker");

        // All VMs have the same peak capacity — utilization model drives actual load
        int[] mipsValues = new int[DataCenterConfig.NUM_VMS];
        Arrays.fill(mipsValues, 100);
        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId(), mipsValues);
        broker.submitGuestList(new ArrayList<>(vms));

        Random rand = new Random(42);
        List<Cloudlet> cloudlets = new ArrayList<>();

        for (int i = 0; i < DataCenterConfig.NUM_VMS; i++) {
            UtilizationModel um = createUtilizationModel(scenario, i, rand);

            // 80,000 MI — at 200 MIPS * 50% avg utilization = 100 effective MIPS
            // finishes in ~800s, so consolidation fires at t=300 and t=600 mid-run
            long length = 80_000L;
            Cloudlet cl = new Cloudlet(i, length, 1, 300, 300, um, um, um);
            cl.setUserId(broker.getId());
            cloudlets.add(cl);
        }
        broker.submitCloudletList(cloudlets);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        double energyKwh = dc.getPower() / 3_600_000.0;
        System.out.printf("Energy:         %.4f kWh%n", energyKwh);
        System.out.printf("VM Migrations:  %d%n", policy.getMigrations());
        System.out.printf("SLA Violations: %d%n", policy.getSlaViolations());
    }

    static UtilizationModel createUtilizationModel(
            WorkloadGenerator.Scenario scenario, int vmIndex, Random rand) {

        switch (scenario) {

            case STEADY: {
                // All VMs oscillate gently around 35% utilization
                // Amplitude of 0.10 means swing between 25% and 45%
                // With 2-3 VMs per host, host utilization stays between 50-135%
                // — enough for some hosts to drift above T_UPPER or below T_LOWER
                // Random phase spreads VMs so they don't all peak simultaneously
                double base      = 0.35;
                double amplitude = 0.10;
                double frequency = 1.0 / 600.0;  // one cycle per 600s
                double phase     = rand.nextDouble() * 2 * Math.PI;
                return new UtilizationModelDynamic(base, amplitude, frequency, phase);
            }

            case PEAK: {
                // All VMs have a low baseline (15-25%) representing off-peak load
                // At t=400s a Gaussian spike pushes a random subset of VMs to ~90%
                // This simulates a rush-hour event mid-simulation
                // Hosts carrying spiking VMs become overloaded; consolidation responds
                double base      = 0.15 + rand.nextDouble() * 0.10;  // 15–25%
                double amplitude = 0.05;
                double frequency = 1.0 / 800.0;
                double phase     = rand.nextDouble() * 2 * Math.PI;

                // ~40% of VMs participate in the spike (roughly 10 out of 25)
                boolean spikes   = rand.nextDouble() < 0.40;
                double peakTime  = 300.0;   // spike centred at t=400s
                double peakAmp   = spikes ? 0.70 : 0.0;  // spiking VMs jump +70%
                double peakWidth = 80.0;    // spike lasts roughly 160s (2 std devs)

                return new UtilizationModelDynamic(
                    base, amplitude, frequency, phase,
                    peakTime, peakAmp, peakWidth);
            }

            case VARIABLE: {
                // Each VM gets a random baseline (10–70%) and random amplitude (5–25%)
                // Random frequencies mean VMs drift independently — genuinely mixed
                // Some hosts will randomly accumulate high-util VMs → overloaded
                // Others will have VMs all in their troughs simultaneously → underloaded
                double base      = 0.10 + rand.nextDouble() * 0.60;  // 10–70%
                double amplitude = 0.05 + rand.nextDouble() * 0.20;  // 5–25%
                double frequency = (0.5 + rand.nextDouble()) / 600.0; // varied cycle lengths
                double phase     = rand.nextDouble() * 2 * Math.PI;
                return new UtilizationModelDynamic(base, amplitude, frequency, phase);
            }

            default:
                throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }
}