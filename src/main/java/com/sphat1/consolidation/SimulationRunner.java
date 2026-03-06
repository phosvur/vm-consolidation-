package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
import java.util.Arrays;
import java.util.*;

public class SimulationRunner {

    public static void main(String[] args) throws Exception {
        for (WorkloadGenerator.Scenario s : WorkloadGenerator.Scenario.values()) {
            System.out.println("\n========== Scenario: " + s + " — HEURISTIC ==========");
            runScenario(s);
            System.out.println("\n========== Scenario: " + s + " — BASELINE ==========");
            BaselineRunner.runScenario(s);
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
            "Datacenter", chars, policy, new LinkedList<>(), 15); //from 300 to 15
        dc.setDisableMigrations(false);

        DatacenterBroker broker = new DatacenterBroker("Broker");

        int[] mipsValues = new int[DataCenterConfig.NUM_VMS];
        Arrays.fill(mipsValues, 100);
        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId(), mipsValues);
        broker.submitGuestList(new ArrayList<>(vms));

        Random rand = new Random(42);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < DataCenterConfig.NUM_VMS; i++) {
            UtilizationModel um = WorkloadGenerator.createUtilizationModel(scenario, i, rand);
//            Cloudlet cl = new Cloudlet(i, 80_000L, 1, 300, 300, um, um, um);
         // Change 80_000L to 8_000_000L to keep the simulation running long enough to measure energy
         // 80 million instructions means at 100 MIPS, it runs for 800,000 seconds
            long length = 80_000_000L; 
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
}