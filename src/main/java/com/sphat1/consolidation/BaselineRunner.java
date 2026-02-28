package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
import java.util.Arrays;
import java.util.*;

public class BaselineRunner {

//    @SuppressWarnings("deprecation")
    static void runScenario(WorkloadGenerator.Scenario scenario) throws Exception {
        // 1. Reset CloudSim for the new scenario
        CloudSim.init(1, Calendar.getInstance(), false);

        List<PowerHost> hosts = DataCenterConfig.createHosts();

        // 2. Baseline Migration Policy (Static Threshold)
        // Using 0.49 to ensure that when utilization hits 50%, it triggers.
        PowerVmAllocationPolicyMigrationStaticThreshold policy =
            new PowerVmAllocationPolicyMigrationStaticThreshold(
                hosts,
                new SelectionPolicyMinimumUtilization(),
                0.49 
            );

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", hosts, 0, 0, 0, 0, 0);

        // 3. CHANGE: Scheduling interval from 300 to 15
        // This is the "heartbeat" that triggers the policy logic.
        PowerDatacenter dc = new PowerDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 15); 
        dc.setDisableMigrations(false);

        DatacenterBroker broker = new DatacenterBroker("Broker");

        int[] mipsValues = new int[DataCenterConfig.NUM_VMS];
        Arrays.fill(mipsValues, 100);

        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId(), mipsValues);

        // 4. FIX: Let the broker and policy handle VM placement.
        // Don't use host.vmCreate(vm) manually; submit the list to the broker.
        broker.submitGuestList(vms);

        Random rand = new Random(42);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < DataCenterConfig.NUM_VMS; i++) {
            UtilizationModel um = WorkloadGenerator.createUtilizationModel(scenario, i, rand);
            // Cloudlet parameters: ID, Length, PEs, FileSize, OutputSize, Utils
            Cloudlet cl = new Cloudlet(i, 80_000L, 1, 300, 300, um, um, um);
            cl.setUserId(broker.getId());
            cloudlets.add(cl);
        }
        broker.submitCloudletList(cloudlets);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        // 5. Results Reporting
        double energyKwh = dc.getPower() / 3_600_000.0;
        System.out.printf("Energy:         %.4f kWh%n", energyKwh);
        
        // Use dc.getMigrationCount() to see how many times the policy moved a VM
        System.out.printf("VM Migrations:  %d%n", dc.getMigrationCount());
        
        // Optional: CloudSim's PowerDatacenter can report SLA through its policy
        // System.out.printf("SLA Violations: %.2f%n", policy.getSlaMetrics().get("sla"));
    }
}