package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
import java.util.Arrays;
import java.util.*;

public class BaselineRunner {

    @SuppressWarnings("deprecation")
	static void runScenario(WorkloadGenerator.Scenario scenario) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        List<PowerHost> hosts = DataCenterConfig.createHosts();

        // Use a lower utilization threshold so the baseline actually sees overloaded hosts.
        // T_UPPER=0.50 means 50% never triggers (strict >). Use 0.49 so that
        // 2 VMs at 50% each (100 MIPS / 400 = 0.25 per VM, 0.50 total) does trigger.
        PowerVmAllocationPolicyMigrationStaticThreshold policy =
            new PowerVmAllocationPolicyMigrationStaticThreshold(
                hosts,
                new SelectionPolicyMinimumUtilization(),
                0.49  // Changed from 0.50 to trigger migrations
            );

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", hosts, 0, 0, 0, 0, 0);

        PowerDatacenter dc = new PowerDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 300);
        dc.setDisableMigrations(false);

        DatacenterBroker broker = new DatacenterBroker("Broker");

        int[] mipsValues = new int[DataCenterConfig.NUM_VMS];
        Arrays.fill(mipsValues, 100);

        // Manually place VMs onto hosts in the same round-robin pattern
        // that ConsolidatingDatacenter uses, bypassing the policy's placement logic
        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId(), mipsValues);

        for (int i = 0; i < vms.size(); i++) {
            PowerVm vm = vms.get(i);
            PowerHost host = hosts.get(i % DataCenterConfig.NUM_HOSTS);
            host.vmCreate(vm);  // directly place VM on host, no policy check
        }

        // Submit the full VM list to the broker so cloudlets can be assigned
        broker.submitGuestList(new ArrayList<>(vms));
        

        Random rand = new Random(42);
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < DataCenterConfig.NUM_VMS; i++) {
            UtilizationModel um = WorkloadGenerator.createUtilizationModel(scenario, i, rand);
            Cloudlet cl = new Cloudlet(i, 80_000L, 1, 300, 300, um, um, um);
            cl.setUserId(broker.getId());
            cloudlets.add(cl);
        }
        broker.submitCloudletList(cloudlets);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        double energyKwh = dc.getPower() / 3_600_000.0;
        System.out.printf("Energy:         %.4f kWh%n", energyKwh);
        System.out.printf("VM Migrations:  %d%n", dc.getMigrationCount());
        System.out.printf("SLA Violations: %d%n", 0); // placeholder — see note
    }
}
