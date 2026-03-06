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

    public static final double T_UPPER = 0.70;
    public static final double T_LOWER = 0.20;

    private int migrations = 0;
    private boolean externalCallAllowed = false;

    private final Map<Integer, Integer> overloadedIntervalCount = new HashMap<>();
    private final Map<Integer, Double>  vmMipsSnapshot          = new HashMap<>();
    private final Map<Integer, Double>  hostMipsSnapshot        = new HashMap<>();
    private final Map<Integer, Double>  committedPeakMips       = new HashMap<>();

    public HeuristicConsolidationPolicy(
            List<? extends Host> hosts,
            SelectionPolicy<GuestEntity> sel) {
        super(hosts, sel);
    }

    /** Called by ConsolidatingDatacenter only — blocks PowerDatacenter's own call. */
    public void setExternalCallAllowed(boolean v) {
        this.externalCallAllowed = v;
    }

    @Override
    public List<GuestMapping> optimizeAllocation(List<? extends GuestEntity> vmList) {
        // Block PowerDatacenter from running this — we call it ourselves
        if (!externalCallAllowed) return new ArrayList<>();

        List<GuestMapping> plan = new ArrayList<>();

        if (org.cloudbus.cloudsim.core.CloudSim.clock() <= 0.1) {
            return plan;
        }

        // ── Step 1: snapshot ──
        vmMipsSnapshot.clear();
        hostMipsSnapshot.clear();
        committedPeakMips.clear();

        for (PowerHost h : this.<PowerHost>getHostList()) {
            double hostTotal = 0;
            for (GuestEntity g : h.getGuestList()) {
                double vmPeakMips = ((Vm) g).getMips();
                double utilFraction = 0;
                for (Cloudlet rc :
                        ((Vm) g).getCloudletScheduler().getCloudletExecList()) {
                    utilFraction += rc.getUtilizationOfCpu(
                        org.cloudbus.cloudsim.core.CloudSim.clock());
                }
                utilFraction = Math.min(1.0, utilFraction);
                double effectiveMips = vmPeakMips * utilFraction;
                vmMipsSnapshot.put(g.getId(), effectiveMips);
                hostTotal += effectiveMips;
            }
            hostMipsSnapshot.put(h.getId(), hostTotal);
        }

        // ── Step 2: classify ──
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
                overloadedIntervalCount.merge(h.getId(), 1, Integer::sum);
            } else if (u < T_LOWER && vmCount > 0) {
                underloaded.add(h);
            }
        }
        System.out.printf("  [Consolidation] Overloaded: %d, Underloaded: %d%n",
            overloaded.size(), underloaded.size());

        // ── Step 3: candidates ──
        List<GuestEntity> candidates = new ArrayList<>();
        for (PowerHost h : overloaded) {
            List<GuestEntity> vmsOnHost = new ArrayList<>(h.getGuestList());
            double allocMips = hostMipsSnapshot.getOrDefault(h.getId(), 0.0);
            double u = allocMips / h.getTotalMips();
            vmsOnHost.sort(Comparator.comparingDouble(
                v -> vmMipsSnapshot.getOrDefault(v.getId(), 0.0)));
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

        // ── Step 4: FFD ──
        candidates.sort((a, b) -> Double.compare(
            vmMipsSnapshot.getOrDefault(b.getId(), 0.0),
            vmMipsSnapshot.getOrDefault(a.getId(), 0.0)));

        Map<Integer, Double> committed = new HashMap<>();

        for (GuestEntity vm : candidates) {
            if (((Vm) vm).isInMigration()) continue;
            PowerHost dest = greedyFit(vm, committed);
            if (dest != null) {
                plan.add(new GuestMapping(vm, dest));
                migrations++;
                double vmMips     = vmMipsSnapshot.getOrDefault(vm.getId(), 0.0);
                double vmPeakMips = ((Vm) vm).getMips();
                committed.merge(dest.getId(), vmMips, Double::sum);
                committedPeakMips.merge(dest.getId(), vmPeakMips, Double::sum);
                System.out.printf("  [Migration] VM #%d (%.0f MIPS) -> Host #%d%n",
                    ((Vm) vm).getId(), vmMips, dest.getId());
            } else {
                System.out.printf("  [Migration] VM #%d -> no suitable host%n",
                    ((Vm) vm).getId());
            }
        }
        System.out.printf("  [Consolidation] Migration plan size: %d%n", plan.size());
        return plan;
    }

    private PowerHost greedyFit(GuestEntity vm, Map<Integer, Double> committed) {
        PowerHost source       = (PowerHost) ((Vm) vm).getHost();
        double vmEffectiveMips = vmMipsSnapshot.getOrDefault(vm.getId(), ((Vm) vm).getMips());
        double vmPeakMips      = ((Vm) vm).getMips();

        PowerHost bestHost = null;
        double bestUtil    = -1;

        for (PowerHost h : this.<PowerHost>getHostList()) {
            if (h.equals(source)) continue;

            double reservedPeak  = h.getGuestList().stream()
                .mapToDouble(g -> ((Vm) g).getMips()).sum();
            double committedPeak = committedPeakMips.getOrDefault(h.getId(), 0.0);
            if (reservedPeak + committedPeak + vmPeakMips > h.getTotalMips()) continue;

            double currentMips   = hostMipsSnapshot.getOrDefault(h.getId(), 0.0);
            double committedMips = committed.getOrDefault(h.getId(), 0.0);
            double newUtil = (currentMips + committedMips + vmEffectiveMips)
                             / h.getTotalMips();

            boolean ramOk = h.getRamProvisioner()
                .isSuitableForGuest((Vm) vm, ((Vm) vm).getRam());
            boolean bwOk  = h.getBwProvisioner()
                .isSuitableForGuest((Vm) vm, ((Vm) vm).getBw());

            if (newUtil <= T_UPPER && ramOk && bwOk && newUtil > bestUtil) {
                bestUtil = newUtil;
                bestHost = h;
            }
        }
        return bestHost;
    }

    @Override
    public boolean isHostOverUtilized(PowerHost host) {
        return host.getUtilizationOfCpu() > T_UPPER;
    }

    public int getMigrations()           { return migrations; }
    public int getSlaViolations()        { return overloadedIntervalCount.size(); }
    public int getTotalOverloadIntervals() {
        return overloadedIntervalCount.values().stream().mapToInt(Integer::intValue).sum();
    }
}