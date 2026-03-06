package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
import java.util.Arrays;
import java.util.*;

public class BaselineRunner {

    static void runScenario(WorkloadGenerator.Scenario scenario) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        // 25 hosts so the baseline policy can place all 25 VMs (1 per host)
        List<PowerHost> hosts = DataCenterConfig.createHosts(DataCenterConfig.NUM_HOSTS_BASELINE);

        PowerVmAllocationPolicyMigrationStaticThreshold policy =
            new PowerVmAllocationPolicyMigrationStaticThreshold(
                hosts,
                new SelectionPolicyMinimumUtilization(),
                HeuristicConsolidationPolicy.T_UPPER
            );

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", hosts, 0, 0, 0, 0, 0);

        PowerDatacenter dc = new PowerDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 15);
        dc.setDisableMigrations(false);

        DatacenterBroker broker = new DatacenterBroker("Broker");

        int[] mipsValues = new int[DataCenterConfig.NUM_VMS];
        Arrays.fill(mipsValues, 150);
        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId(), mipsValues);
        broker.submitGuestList(vms);

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

        double energyKwh = dc.getPower() / 3_600_000.0;
        int migrations   = dc.getMigrationCount();

        System.out.printf("Energy:         %.4f kWh%n", energyKwh);
        System.out.printf("VM Migrations:  %d%n", migrations);
        System.out.printf("SLA Violations: N/A (not tracked by baseline policy)%n");

        ResultAggregator.collect(
            scenario.toString() + "-BASELINE",
            energyKwh, migrations, -1.0
        );
    }
}