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
        ResultAggregator.printReport();
    }

    static void runScenario(WorkloadGenerator.Scenario scenario) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        // 10 hosts: heuristic will consolidate 25 VMs onto fewer active hosts
        List<PowerHost> hosts = DataCenterConfig.createHosts(DataCenterConfig.NUM_HOSTS_HEURISTIC);

        HeuristicConsolidationPolicy policy =
            new HeuristicConsolidationPolicy(hosts,
                new SelectionPolicyMinimumUtilization());

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", hosts, 0, 0, 0, 0, 0);

        ConsolidatingDatacenter dc = new ConsolidatingDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 15);
        dc.setDisableMigrations(false);

        DatacenterBroker broker = new DatacenterBroker("Broker");

        int[] mipsValues = new int[DataCenterConfig.NUM_VMS];
        Arrays.fill(mipsValues, 150);
        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId(), mipsValues);
        broker.submitGuestList(new ArrayList<>(vms));

        Random rand = new Random(42);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < DataCenterConfig.NUM_VMS; i++) {
            UtilizationModel um =
                WorkloadGenerator.createUtilizationModel(scenario, i, rand);
            long length = 1_000_000_000L;
            Cloudlet cl = new Cloudlet(i, length, 1, 300, 300, um, um, um);
            cl.setUserId(broker.getId());
            cloudlets.add(cl);
        }
        broker.submitCloudletList(cloudlets);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        double energyKwh        = dc.getPower() / 3_600_000.0;
        int migrations          = policy.getMigrations();
        int slaViolations       = policy.getSlaViolations();
        int totalOverloadEvents = policy.getTotalOverloadIntervals();

        System.out.printf("Energy:               %.4f kWh%n", energyKwh);
        System.out.printf("VM Migrations:        %d%n", migrations);
        System.out.printf("SLA Violations:       %d distinct hosts overloaded%n", slaViolations);
        System.out.printf("Overload intervals:   %d host-interval events%n", totalOverloadEvents);

        ResultAggregator.collect(
            scenario.toString() + "-HEURISTIC",
            energyKwh, migrations, (double) slaViolations
        );
    }
}