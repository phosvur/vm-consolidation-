package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
import java.util.Arrays;
import java.util.*;

public class BaselineRunner {

    static SimulationRunner.SimulationMetrics runScenario(WorkloadGenerator.Scenario scenario) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);

        List<PowerHost> hosts = DataCenterConfig.createHosts();

        // BUG FIX: Keep a reference to the policy so we can query its migration
        // counter directly after the simulation. PowerDatacenter.getMigrationCount()
        // is not reliably populated in all CloudSim 7.x builds, but the policy
        // tracks its own executed migrations internally.
        BaselineMigrationTracker policy = new BaselineMigrationTracker(
            hosts,
            new SelectionPolicyMinimumUtilization(),
            0.49
        );

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", hosts, 0, 0, 0, 0, 0);

        PowerDatacenter dc = new PowerDatacenter(
            "Datacenter", chars, policy, new LinkedList<>(), 15);
        dc.setDisableMigrations(false);

        DatacenterBroker broker = new DatacenterBroker("Broker");

        int[] mipsValues = new int[DataCenterConfig.NUM_VMS];
        Arrays.fill(mipsValues, 100);

        List<PowerVm> vms = DataCenterConfig.createVms(broker.getId(), mipsValues);
        broker.submitGuestList(vms);

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

        double energyKwh    = dc.getPower() / 3_600_000.0;

        // BUG FIX: Read migration count from the policy object, not dc.getMigrationCount().
        // dc.getMigrationCount() may return 0 in CloudSim 7.x because it only counts
        // migrations initiated through the internal event bus, whereas the policy's own
        // counter captures every call to optimizeAllocation() that produced a migration plan.
        int migrations      = policy.getMigrations();

        // BUG FIX: SLA violations now measured the same way as the heuristic —
        // by checking per-tick whether host demand exceeds capacity.
        int slaViolations   = policy.getSlaViolationEvents();
        double slaViolTime  = policy.getSlaViolationTime();

        System.out.printf("Energy:            %.4f kWh%n",    energyKwh);
        System.out.printf("VM Migrations:     %d%n",          migrations);
        System.out.printf("SLA Viol. Events:  %d%n",          slaViolations);
        System.out.printf("SLA Viol. Time:    %.2f s%n",      slaViolTime);

        return new SimulationRunner.SimulationMetrics(energyKwh, migrations, slaViolations, slaViolTime);
    }

    /**
     * Thin wrapper around PowerVmAllocationPolicyMigrationStaticThreshold that
     * tracks migration count and SLA violations using the same methodology as
     * HeuristicConsolidationPolicy, enabling a fair apples-to-apples comparison.
     */
    static class BaselineMigrationTracker
            extends PowerVmAllocationPolicyMigrationStaticThreshold {

        private int    migrations          = 0;
        private int    slaViolationEvents  = 0;
        private double slaViolationTime    = 0.0;
        private double lastTickTime        = 0.0;

        // Snapshot maps mirroring the heuristic policy
        private final Map<Integer, Double> vmMipsSnapshot   = new HashMap<>();
        private final Map<Integer, Double> hostMipsSnapshot = new HashMap<>();

        BaselineMigrationTracker(
                List<PowerHost> hosts,
                SelectionPolicyMinimumUtilization sel,
                double threshold) {
            super(hosts, sel, threshold);
        }

        @Override
        public List<GuestMapping> optimizeAllocation(List<? extends GuestEntity> vmList) {
            double now         = org.cloudbus.cloudsim.core.CloudSim.clock();
            double tickDuration = now - lastTickTime;
            lastTickTime       = now;

            // Build snapshots for SLA measurement
            vmMipsSnapshot.clear();
            hostMipsSnapshot.clear();

            for (PowerHost h : this.<PowerHost>getHostList()) {
                double hostTotal = 0;
                for (GuestEntity g : h.getGuestList()) {
                    Vm vm = (Vm) g;
                    double utilFraction = 0;
                    for (Cloudlet rc : vm.getCloudletScheduler().getCloudletExecList()) {
                        utilFraction += rc.getUtilizationOfCpu(now);
                    }
                    utilFraction = Math.min(1.0, utilFraction);
                    double effectiveMips = vm.getMips() * utilFraction;
                    vmMipsSnapshot.put(g.getId(), effectiveMips);
                    hostTotal += effectiveMips;
                }
                hostMipsSnapshot.put(h.getId(), hostTotal);
            }

            // Measure SLA violations (same logic as heuristic)
            if (now > 0.1) {
                for (PowerHost h : this.<PowerHost>getHostList()) {
                    double demandedMips  = hostMipsSnapshot.getOrDefault(h.getId(), 0.0);
                    double capacityMips  = h.getTotalMips();
                    if (demandedMips > capacityMips && !h.getGuestList().isEmpty()) {
                        double oversubscription = demandedMips - capacityMips;
                        for (GuestEntity g : h.getGuestList()) {
                            double vmDemand = vmMipsSnapshot.getOrDefault(g.getId(), 0.0);
                            if (vmDemand > 0) {
                                double violFraction = Math.min(1.0, oversubscription / demandedMips);
                                slaViolationEvents++;
                                slaViolationTime += violFraction * tickDuration;
                            }
                        }
                    }
                }
            }

            // Delegate to parent for the actual migration plan
            List<GuestMapping> plan = super.optimizeAllocation(vmList);
            migrations += plan.size();
            return plan;
        }

        int    getMigrations()          { return migrations;         }
        int    getSlaViolationEvents()  { return slaViolationEvents; }
        double getSlaViolationTime()    { return slaViolationTime;   }
    }
}