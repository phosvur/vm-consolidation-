package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
import org.cloudbus.cloudsim.core.GuestEntity;
import java.util.*;

public class SimulationRunner {

    public static void main(String[] args) throws Exception {
        for (WorkloadGenerator.Scenario s : WorkloadGenerator.Scenario.values()) {
            System.out.println("========== Scenario: " + s + " ==========");
            runScenario(s);
        }
    }

    static void runScenario(WorkloadGenerator.Scenario scenario)
            throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        List<PowerHost> hosts = DataCenterConfig.createHosts();

        HeuristicConsolidationPolicy policy =
            new HeuristicConsolidationPolicy(hosts,
                new SelectionPolicyMinimumUtilization());

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
            "x86","Linux","Xen", hosts, 0, 0, 0, 0, 0);
        PowerDatacenter dc = new PowerDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 300);

        DatacenterBroker broker = new DatacenterBroker("Broker");
        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId());
        broker.submitVmList(new ArrayList<>(vms));

        List<Cloudlet> cloudlets = new ArrayList<>();
        UtilizationModel um = WorkloadGenerator.get(scenario, 42L);
        for (int i = 0; i < DataCenterConfig.NUM_VMS; i++) {
            Cloudlet cl = new Cloudlet(i, 100_000, 1, 300, 300, um, um, um);
            cl.setUserId(broker.getId());
            cloudlets.add(cl);
        }
        broker.submitCloudletList(cloudlets);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        double energyKwh = dc.getPower() / (3_600_000.0);
        System.out.printf("Energy:         %.4f kWh%n", energyKwh);
        System.out.printf("VM Migrations:  %d%n",  policy.getMigrations());
        System.out.printf("SLA Violations: %d%n",  policy.getSlaViolations());
    }
}