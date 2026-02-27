package com.sphat1.consolidation;
import com.sphat1.consolidation.ConsolidatingDatacenter;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
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

     // Make sure BOTH the type AND constructor say ConsolidatingDatacenter:
        ConsolidatingDatacenter dc = new ConsolidatingDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 300);
        dc.setDisableMigrations(false);

        DatacenterBroker broker = new DatacenterBroker("Broker");

        int[] mipsValues = generateMips(scenario);
        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId(), mipsValues);
        broker.submitGuestList(new ArrayList<>(vms));

        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < DataCenterConfig.NUM_VMS; i++) {
            UtilizationModel um = new UtilizationModelFull();
            // length = 20000 MI; slowest VM is 20 MIPS → finishes in 20000/20 = 1000s
            // scheduling interval = 300s → consolidation fires at t=300, t=600, t=900
            // before cloudlets finish at t=1000
            long length = 20_000L;
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

    static int[] generateMips(WorkloadGenerator.Scenario scenario) {
        Random rand = new Random(42);
        int[] mips = new int[DataCenterConfig.NUM_VMS];

        for (int i = 0; i < DataCenterConfig.NUM_VMS; i++) {
            mips[i] = switch (scenario) {
                // STEADY: 2-3 VMs per host, each 20-40 MIPS
                // avg ~2.5 * 30 = 75 MIPS / 200 = 37.5% utilization → well within bounds
                case STEADY -> 20 + rand.nextInt(20);   // 20–40 MIPS

                // PEAK: intentionally overload ~3 hosts, leave ~7 hosts as migration targets
                // Strategy: 6 "heavy" VMs at 90-110 MIPS placed 2-per-host on 3 hosts → >90%
                // remaining 19 VMs at 20-35 MIPS fill the other 7 hosts at 25-50%
                case PEAK -> (i < 6)
                    ? 90 + rand.nextInt(20)             // 90–110 MIPS (overloads host)
                    : 20 + rand.nextInt(15);            // 20–35 MIPS (normal)

                // VARIABLE: bimodal — some hosts underloaded (<20%), some overloaded (>80%)
                // light VMs: 5-15 MIPS; heavy VMs: 85-100 MIPS
                case VARIABLE -> (rand.nextBoolean())
                    ? 5 + rand.nextInt(10)              // 5–15 MIPS (very light)
                    : 85 + rand.nextInt(15);            // 85–100 MIPS (very heavy)
            };
        }
        return mips;
    }
}