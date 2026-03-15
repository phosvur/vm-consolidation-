package com.sphat1.consolidation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyMigrationAbstract;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicy;

@SuppressWarnings("deprecation")
public class HeuristicConsolidationPolicy
        extends PowerVmAllocationPolicyMigrationAbstract {

    static final double T_UPPER = 0.50;
    static final double T_LOWER = 0.20;

    private int migrations    = 0;

    // BUG FIX: SLA violations are now counted at the VM level, not per overloaded host.
    // An SLA violation occurs when a VM's CPU demand cannot be fully satisfied by its host.
    // We track total violation-seconds (time * unmet fraction) for a richer metric,
    // plus a simple event counter.
    private int    slaViolationEvents   = 0;   // number of VM-ticks where demand > supply
    private double slaViolationTime     = 0.0; // total seconds of violation across all VMs

    // Snapshot: vmId → effective (utilized) MIPS at snapshot time
    private final Map<Integer, Double> vmMipsSnapshot    = new HashMap<>();
    // Snapshot: hostId → total effective MIPS across all its VMs
    private final Map<Integer, Double> hostMipsSnapshot  = new HashMap<>();
    // Track committed peak MIPS per host within one planning cycle
    private final Map<Integer, Double> committedPeakMips = new HashMap<>();

    // Time of last consolidation tick (used to compute violation-seconds)
    private double lastTickTime = 0.0;

    public HeuristicConsolidationPolicy(
            List<? extends Host> hosts,
            SelectionPolicy<GuestEntity> sel) {
        super(hosts, sel);
    }

    @Override
    public List<GuestMapping> optimizeAllocation(List<? extends GuestEntity> vmList) {
        List<GuestMapping> plan = new ArrayList<>();

        // ── Step 1: snapshot MIPS allocations ──
        vmMipsSnapshot.clear();
        hostMipsSnapshot.clear();
        committedPeakMips.clear();

        double now = org.cloudbus.cloudsim.core.CloudSim.clock();
        double tickDuration = now - lastTickTime;
        lastTickTime = now;

        // Skip t=0 startup artifacts
        if (now <= 0.1) {
            System.out.println("  [Consolidation] Skipping snapshot at t=" + now);
            return new ArrayList<>();
        }

        for (PowerHost h : this.<PowerHost>getHostList()) {
            double hostTotal = 0;
            for (GuestEntity g : h.getGuestList()) {
                Vm vm = (Vm) g;
                double vmPeakMips = vm.getMips();
                double utilFraction = 0;
                for (Cloudlet rc : vm.getCloudletScheduler().getCloudletExecList()) {
                    utilFraction += rc.getUtilizationOfCpu(now);
                }
                utilFraction = Math.min(1.0, utilFraction);
                double effectiveMips = vmPeakMips * utilFraction;
                vmMipsSnapshot.put(g.getId(), effectiveMips);
                hostTotal += effectiveMips;
            }
            hostMipsSnapshot.put(h.getId(), hostTotal);
        }

        // ── Step 2: classify hosts + measure SLA violations ──
        // BUG FIX: SLA violations are measured per VM, per tick.
        // A violation occurs when the host's total demanded MIPS exceeds its capacity,
        // meaning some VMs are getting less than they asked for.
        for (PowerHost h : this.<PowerHost>getHostList()) {
            double demandedMips = hostMipsSnapshot.getOrDefault(h.getId(), 0.0);
            double capacityMips = h.getTotalMips();

            if (demandedMips > capacityMips && !h.getGuestList().isEmpty()) {
                // Oversubscription ratio: how much demand exceeds supply
                double oversubscription = demandedMips - capacityMips;
                // Each VM on this host experiences a proportional violation
                for (GuestEntity g : h.getGuestList()) {
                    double vmDemand = vmMipsSnapshot.getOrDefault(g.getId(), 0.0);
                    if (vmDemand > 0) {
                        double vmViolationFraction = Math.min(1.0, oversubscription / demandedMips);
                        slaViolationEvents++;
                        slaViolationTime += vmViolationFraction * tickDuration;
                    }
                }
            }
        }

        // ── Step 3: classify hosts for migration decisions ──
        List<PowerHost> overloaded  = new ArrayList<>();
        List<PowerHost> underloaded = new ArrayList<>();

        for (PowerHost h : this.<PowerHost>getHostList()) {
            double allocMips = hostMipsSnapshot.getOrDefault(h.getId(), 0.0);
            double u = allocMips / h.getTotalMips();
            int vmCount = h.getGuestList().size();
            System.out.printf("  [Consolidation] Host #%d snapshotUtil=%.2f vms=%d%n",
                h.getId(), u, vmCount);
            if (u > T_UPPER) {
                overloaded.add(h);
            } else if (u < T_LOWER && vmCount > 0) {
                underloaded.add(h);
            }
        }
        System.out.printf("  [Consolidation] Overloaded: %d, Underloaded: %d%n",
            overloaded.size(), underloaded.size());

        // ── Step 4: select candidate VMs ──
        List<GuestEntity> candidates = new ArrayList<>();

        for (PowerHost h : overloaded) {
            List<GuestEntity> vmsOnHost = new ArrayList<>(h.getGuestList());
            double allocMips = hostMipsSnapshot.getOrDefault(h.getId(), 0.0);
            double u = allocMips / h.getTotalMips();
            vmsOnHost.sort(Comparator.comparingDouble(
                v -> vmMipsSnapshot.getOrDefault(v.getId(), 0.0)
            ));
            for (GuestEntity v : vmsOnHost) {
                if (u <= T_UPPER) break;
                candidates.add(v);
                u -= vmMipsSnapshot.getOrDefault(v.getId(), 0.0) / h.getTotalMips();
            }
        }

        for (PowerHost h : underloaded) {
            candidates.addAll(h.getGuestList());
        }

        System.out.printf("  [Consolidation] Candidates: %d%n", candidates.size());

        // ── Step 5: FFD — sort by DECREASING MIPS ──
        candidates.sort((a, b) -> Double.compare(
            vmMipsSnapshot.getOrDefault(b.getId(), 0.0),
            vmMipsSnapshot.getOrDefault(a.getId(), 0.0)
        ));

        Map<Integer, Double> committed = new HashMap<>();

        for (GuestEntity vm : candidates) {
            if (((Vm) vm).isInMigration()) continue;

            PowerHost dest = greedyFit(vm, committed);
            if (dest != null) {
                plan.add(new GuestMapping(vm, dest));
                migrations++;
                
                // Safe retrieval with default
                double vmMips     = vmMipsSnapshot.getOrDefault(vm.getId(), 0.0);
                double vmPeakMips = ((Vm) vm).getMips();

                // Merge safely — never null
                committed.merge(dest.getId(), vmMips, (oldVal, newVal) -> {
                    if (oldVal == null) return newVal;
                    if (newVal == null) return oldVal;
                    return oldVal + newVal;
                });
                committedPeakMips.merge(dest.getId(), vmPeakMips, (oldVal, newVal) -> {
                    if (oldVal == null) return newVal;
                    if (newVal == null) return oldVal;
                    return oldVal + newVal;
                });

                System.out.printf("  [Migration] VM #%d (%.0f MIPS) → Host #%d%n",
                    ((Vm) vm).getId(), vmMips, dest.getId());
            } else {
                System.out.printf("  [Migration] VM #%d → no suitable host%n",
                    ((Vm) vm).getId());
            }
        }   

        System.out.printf("  [Consolidation] Migration plan size: %d%n", plan.size());
        return plan;

    }

    private PowerHost greedyFit(GuestEntity vm, Map<Integer, Double> committed) {
        PowerHost source = (PowerHost) ((Vm) vm).getHost();
        double vmEffectiveMips = vmMipsSnapshot.getOrDefault(vm.getId(), ((Vm) vm).getMips());
        double vmPeakMips = ((Vm) vm).getMips();

        PowerHost bestHost = null;
        double bestUtil = -1;

        for (PowerHost h : this.<PowerHost>getHostList()) {
            if (h.equals(source)) continue;

            double reservedPeak  = h.getGuestList().stream()
                .mapToDouble(g -> ((Vm) g).getMips()).sum();
            double committedPeak = committedPeakMips.getOrDefault(h.getId(), 0.0);
            if (reservedPeak + committedPeak + vmPeakMips > h.getTotalMips()) continue;

            double currentMips   = hostMipsSnapshot.getOrDefault(h.getId(), 0.0);
            double committedMips = committed.getOrDefault(h.getId(), 0.0);
            double newUtil = (currentMips + committedMips + vmEffectiveMips) / h.getTotalMips();

            boolean ramOk = h.getRamProvisioner().isSuitableForGuest((Vm) vm, ((Vm) vm).getRam());
            boolean bwOk  = h.getBwProvisioner().isSuitableForGuest((Vm) vm, ((Vm) vm).getBw());

            if (newUtil <= T_UPPER && ramOk && bwOk) {
                if (newUtil > bestUtil) {
                    bestUtil = newUtil;
                    bestHost = h;
                }
            }
        }
        return bestHost;
    }

    @Override
    public boolean isHostOverUtilized(PowerHost host) {
        return host.getUtilizationOfCpu() > T_UPPER;
    }

    public int getMigrations()         { return migrations;          }
    public int getSlaViolationEvents() { return slaViolationEvents;  }
    public double getSlaViolationTime(){ return slaViolationTime;    }

    // Convenience: keep old getSlaViolations() name pointing to event count
    public int getSlaViolations()      { return slaViolationEvents;  }
}