package com.sphat1.consolidation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyMigrationAbstract;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicy;

public class HeuristicConsolidationPolicy
        extends PowerVmAllocationPolicyMigrationAbstract {

    static final double T_UPPER = 0.80;
    static final double T_LOWER = 0.20;

    private int migrations    = 0;
    private int slaViolations = 0;

    // Cache: vmId → (vmMips, sourceHostId) captured while VMs are still alive
    private final Map<Integer, double[]> vmSnapshot = new HashMap<>();

    public HeuristicConsolidationPolicy(
            List<? extends Host> hosts,
            SelectionPolicy<GuestEntity> sel) {
        super(hosts, sel);
    }

    @Override
    public List<GuestMapping> optimizeAllocation(List<? extends GuestEntity> vmList) {
        List<GuestMapping> plan = new ArrayList<>();

        // ── Step 1: snapshot current state while VMs are still on hosts ──
        vmSnapshot.clear();
        for (PowerHost h : this.<PowerHost>getHostList()) {
            for (GuestEntity g : h.getGuestList()) {
                double mips = h.getTotalAllocatedMipsForGuest(g);
                vmSnapshot.put(g.getId(), new double[]{mips, h.getId()});
            }
        }

        // ── Step 2: classify hosts ──
        List<PowerHost> overloaded  = new ArrayList<>();
        List<PowerHost> underloaded = new ArrayList<>();

        for (PowerHost h : this.<PowerHost>getHostList()) {
            double u = h.getUtilizationOfCpu();
            System.out.printf("  [Consolidation] Host #%d util=%.2f vms=%d%n",
                h.getId(), u, h.getGuestList().size());
            if (u > T_UPPER) {
                overloaded.add(h);
                slaViolations++;
            } else if (u < T_LOWER && !h.getGuestList().isEmpty()) {
                underloaded.add(h);
            }
        }
        System.out.printf("  [Consolidation] Overloaded: %d, Underloaded: %d%n",
            overloaded.size(), underloaded.size());

        // ── Step 3: select candidate VMs to migrate ──
        List<GuestEntity> candidates = new ArrayList<>();

        for (PowerHost h : overloaded) {
            List<GuestEntity> vmsOnHost = new ArrayList<>(h.getGuestList());
            System.out.printf("  [DEBUG] Overloaded Host #%d has %d VMs%n",
                h.getId(), vmsOnHost.size());
            // sort ascending by allocated MIPS — move lightest first
            vmsOnHost.sort(Comparator.comparingDouble(
                v -> h.getTotalAllocatedMipsForGuest(v)
            ));
            double u = h.getUtilizationOfCpu();
            for (GuestEntity v : vmsOnHost) {
                if (u <= T_UPPER) break;
                candidates.add(v);
                u -= h.getTotalAllocatedMipsForGuest(v) / h.getTotalMips();
            }
        }

        for (PowerHost h : underloaded) {
            System.out.printf("  [DEBUG] Underloaded Host #%d has %d VMs%n",
                h.getId(), h.getGuestList().size());
            candidates.addAll(h.getGuestList());
        }

        System.out.printf("  [Consolidation] Candidates: %d%n", candidates.size());

        // ── Step 4: FFD placement using snapshot MIPS values ──
        candidates.sort((a, b) -> {
            double mipsA = vmSnapshot.containsKey(a.getId()) ? vmSnapshot.get(a.getId())[0] : 0;
            double mipsB = vmSnapshot.containsKey(b.getId()) ? vmSnapshot.get(b.getId())[0] : 0;
            return Double.compare(mipsB, mipsA); // descending
        });

        // Track planned utilization increases per host
        Map<Integer, Double> plannedExtra = new HashMap<>();

        for (GuestEntity vm : candidates) {
            PowerHost dest = greedyFit(vm, plannedExtra);
            if (dest != null) {
                plan.add(new GuestMapping(vm, dest));
                migrations++;
                // account for this VM's MIPS in future placements
                double vmMips = vmSnapshot.containsKey(vm.getId())
                    ? vmSnapshot.get(vm.getId())[0] : 0;
                plannedExtra.merge(dest.getId(),
                    vmMips / dest.getTotalMips(), Double::sum);
                System.out.printf("  [Migration] VM #%d → Host #%d%n",
                    vm.getId(), dest.getId());
            } else {
                System.out.printf("  [Migration] VM #%d → no suitable host found%n",
                    vm.getId());
            }
        }

        System.out.printf("  [Consolidation] Migration plan size: %d%n", plan.size());
        return plan;
    }

    private PowerHost greedyFit(GuestEntity vm, Map<Integer, Double> plannedExtra) {
        PowerHost source = (PowerHost) ((Vm) vm).getHost();
        double vmMips = vmSnapshot.containsKey(vm.getId())
            ? vmSnapshot.get(vm.getId())[0]
            : ((Vm) vm).getMips();

        for (PowerHost h : this.<PowerHost>getHostList()) {
            if (h.equals(source)) continue;
            double extra = plannedExtra.getOrDefault(h.getId(), 0.0);
            double newUtil = h.getUtilizationOfCpu() + extra + vmMips / h.getTotalMips();
            boolean cpuOk = newUtil <= T_UPPER;
            boolean suitable = h.isSuitableForGuest(vm);
            System.out.printf("    greedyFit VM#%d → Host#%d: newUtil=%.2f cpuOk=%b suitable=%b%n",
                vm.getId(), h.getId(), newUtil, cpuOk, suitable);
            if (cpuOk && suitable) {
                return h;
            }
        }
        return null;
    }

    @Override
    public boolean isHostOverUtilized(PowerHost host) {
        return host.getUtilizationOfCpu() > T_UPPER;
    }

    public int getMigrations()    { return migrations;    }
    public int getSlaViolations() { return slaViolations; }
}