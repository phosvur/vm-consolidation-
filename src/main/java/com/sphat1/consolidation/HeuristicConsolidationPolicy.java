package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicy;
import java.util.*;

public class HeuristicConsolidationPolicy
        extends PowerVmAllocationPolicyMigrationAbstract {

    static final double T_UPPER = 0.80;
    static final double T_LOWER = 0.20;

    private int migrations   = 0;
    private int slaViolations = 0;

    public HeuristicConsolidationPolicy(
            List<? extends Host> hosts,
            SelectionPolicy<GuestEntity> sel) {
        super(hosts, sel);
        setUnderUtilizationThreshold(T_LOWER);
    }

    @Override
    public List<Map<String, Object>> optimizeAllocation(
            List<? extends Vm> vmList) {

        List<Map<String, Object>> plan = new ArrayList<>();
        List<PowerHost> overloaded  = new ArrayList<>();
        List<PowerHost> underloaded = new ArrayList<>();

        // Step 2: Classify hosts (Equation 4)
        for (PowerHost h : this.<PowerHost>getHostList()) {
            double u = h.getUtilizationOfCpu();
            if      (u > T_UPPER)               { overloaded.add(h);  slaViolations++; }
            else if (u < T_LOWER && !h.getVmList().isEmpty()) underloaded.add(h);
        }

        List<Vm> candidates = new ArrayList<>();

        // Step 3: Overload handling — minimum utilization selection
        for (PowerHost h : overloaded) {
            List<Vm> sorted = new ArrayList<>(h.getVmList());
            sorted.sort(Comparator.comparingDouble(
                v -> ((PowerVm)v).getTotalUtilizationOfCpuMips(CloudSim.clock())));
            double u = h.getUtilizationOfCpu();
            for (Vm v : sorted) {
                if (u <= T_UPPER) break;
                candidates.add(v);
                u -= ((PowerVm)v).getTotalUtilizationOfCpuMips(CloudSim.clock())
                     / h.getTotalMips();
            }
        }

        // Step 4: Underload handling — evacuate whole host
        for (PowerHost h : underloaded) candidates.addAll(h.getVmList());

        // Step 5: Greedy placement — sort by decreasing CPU demand (FFD)
        candidates.sort((a, b) -> Double.compare(
            ((PowerVm)b).getTotalUtilizationOfCpuMips(CloudSim.clock()),
            ((PowerVm)a).getTotalUtilizationOfCpuMips(CloudSim.clock())));

        for (Vm vm : candidates) {
            PowerHost dest = greedyFit(vm, (PowerHost) vm.getHost());
            if (dest != null) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("vm",   vm);
                entry.put("host", dest);
                plan.add(entry);
                migrations++;
            }
        }
        return plan;
    }

    // First Fit Decreasing placement from Section 3.3
    private PowerHost greedyFit(Vm vm, PowerHost exclude) {
        double vmMips = ((PowerVm)vm)
            .getTotalUtilizationOfCpuMips(CloudSim.clock());
        for (PowerHost h : this.<PowerHost>getHostList()) {
            if (h.equals(exclude)) continue;
            double newUtil = h.getUtilizationOfCpu()
                           + vmMips / h.getTotalMips();
            if (newUtil <= T_UPPER && h.isSuitableForVm(vm)) return h;
        }
        return null;
    }

    @Override
    protected boolean isHostOverloaded(PowerHost h) {
        return h.getUtilizationOfCpu() > T_UPPER;
    }

    public int getMigrations()    { return migrations;    }
    public int getSlaViolations() { return slaViolations; }
}