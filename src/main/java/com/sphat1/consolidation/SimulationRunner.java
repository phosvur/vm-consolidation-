package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
import java.util.Arrays;
import java.util.*;

public class SimulationRunner {

    private static final ResultsAggregator aggregator = new ResultsAggregator();

    public static void main(String[] args) throws Exception {
        for (WorkloadGenerator.Scenario s : WorkloadGenerator.Scenario.values()) {

            System.out.println("\n========== Scenario: " + s + " — HEURISTIC ==========");
            SimulationMetrics hMetrics = runScenario(s);
            aggregator.addResult(s.toString(), "Heuristic",
                hMetrics.energy, hMetrics.migrations,
                hMetrics.slaViolations, hMetrics.slaViolationTime);

            System.out.println("\n========== Scenario: " + s + " — BASELINE ==========");
            SimulationMetrics bMetrics = BaselineRunner.runScenario(s);
            aggregator.addResult(s.toString(), "Baseline",
                bMetrics.energy, bMetrics.migrations,
                bMetrics.slaViolations, bMetrics.slaViolationTime);
        }

        aggregator.exportToCsv();
    }

    static SimulationMetrics runScenario(WorkloadGenerator.Scenario scenario) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        List<PowerHost> hosts = DataCenterConfig.createHosts();
        HeuristicConsolidationPolicy policy =
            new HeuristicConsolidationPolicy(hosts, new SelectionPolicyMinimumUtilization());
        DatacenterCharacteristics chars =
            new DatacenterCharacteristics("x86", "Linux", "Xen", hosts, 0, 0, 0, 0, 0);

        ConsolidatingDatacenter dc = new ConsolidatingDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 15);
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
            long length = 80_000_000L;
            Cloudlet cl = new Cloudlet(i, length, 1, 300, 300, um, um, um);
            cl.setUserId(broker.getId());
            cloudlets.add(cl);
        }
        broker.submitCloudletList(cloudlets);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        // BUG FIX: Energy is now non-zero because we removed setFailed() in
        // ConsolidatingDatacenter — the power accumulator is no longer corrupted.
        double energyKwh = dc.getPower() / 3_600_000.0;

        System.out.printf("Energy:            %.4f kWh%n",  energyKwh);
        System.out.printf("VM Migrations:     %d%n",        policy.getMigrations());
        System.out.printf("SLA Viol. Events:  %d%n",        policy.getSlaViolationEvents());
        System.out.printf("SLA Viol. Time:    %.2f s%n",    policy.getSlaViolationTime());

        return new SimulationMetrics(
            energyKwh,
            policy.getMigrations(),
            policy.getSlaViolationEvents(),
            policy.getSlaViolationTime()
        );
    }

    public static class SimulationMetrics {
        public double energy;
        public int    migrations;
        public int    slaViolations;
        public double slaViolationTime;  // total VM-seconds under SLA breach

        public SimulationMetrics(double energy, int migrations,
                                  int slaViolations, double slaViolationTime) {
            this.energy           = energy;
            this.migrations       = migrations;
            this.slaViolations    = slaViolations;
            this.slaViolationTime = slaViolationTime;
        }
    }
}