package com.sphat1.consolidation;

//import org.cloudbus.cloudsim.GuestMapping;   // added for 7G
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerVm;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyMigrationAbstract;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicy;

public class HeuristicConsolidationPolicy
        extends PowerVmAllocationPolicyMigrationAbstract {

    static final double T_UPPER = 0.80;
    static final double T_LOWER = 0.20;

    private int migrations    = 0;
    private int slaViolations = 0;

    public HeuristicConsolidationPolicy(
            List<? extends Host> hosts,
            SelectionPolicy<GuestEntity> sel) {
        super(hosts, sel);
    }

    // 7G uses GuestEntity and expects return value List<GuestMapping>
    @Override
    public List<GuestMapping> optimizeAllocation(
            List<? extends GuestEntity> vmList) {

        List<GuestMapping> plan = new ArrayList<>();   // was List<Map<String, Object>>
        List<PowerHost> overloaded  = new ArrayList<>();
        List<PowerHost> underloaded = new ArrayList<>();

        // Classify hosts per Equation 4
        for (PowerHost h : this.<PowerHost>getHostList()) {
            double u = h.getUtilizationOfCpu();
            if (u > T_UPPER) {
                overloaded.add(h);
                slaViolations++;
            } else if (u < T_LOWER && !h.getGuestList().isEmpty()) {
                underloaded.add(h);
            }
        }

        List<GuestEntity> candidates = new ArrayList<>();

        // Overload handling — minimum utilization VM selection
        for (PowerHost h : overloaded) {
            List<GuestEntity> vmsOnHost = new ArrayList<>(h.getGuestList());
            vmsOnHost.sort(Comparator.comparingDouble(
                v -> ((PowerVm) v).getTotalUtilizationOfCpuMips(CloudSim.clock())
            ));
            double u = h.getUtilizationOfCpu();
            for (GuestEntity v : vmsOnHost) {
                if (u <= T_UPPER) break;
                candidates.add(v);
                u -= ((PowerVm) v).getTotalUtilizationOfCpuMips(CloudSim.clock())
                     / h.getTotalMips();
            }
        }

        // Underload handling — evacuate entire host
        for (PowerHost h : underloaded) {
            candidates.addAll(h.getGuestList());
        }

        // Greedy placement — sort by decreasing CPU demand (FFD)
        candidates.sort((a, b) -> Double.compare(
            ((PowerVm) b).getTotalUtilizationOfCpuMips(CloudSim.clock()),
            ((PowerVm) a).getTotalUtilizationOfCpuMips(CloudSim.clock())
        ));

        for (GuestEntity vm : candidates) {
            PowerHost dest = greedyFit(vm);
            if (dest != null) {
                plan.add(new GuestMapping(vm, dest));   // was a Map entry
                migrations++;
            }
        }
        return plan;
    }

    // First Fit Decreasing — finds destination host without exceeding T_UPPER
    private PowerHost greedyFit(GuestEntity vm) {
        PowerHost source = (PowerHost) ((Vm) vm).getHost();
        double vmMips = ((PowerVm) vm)
            .getTotalUtilizationOfCpuMips(CloudSim.clock());

        for (PowerHost h : this.<PowerHost>getHostList()) {
            if (h.equals(source)) continue;
            double newUtil = h.getUtilizationOfCpu()
                           + vmMips / h.getTotalMips();
            if (newUtil <= T_UPPER && h.isSuitableForGuest(vm)) {
                return h;
            }
        }
        return null;
    }

    // 7G renamed this from isHostOverloaded to isHostOverUtilized
    @Override
    public boolean isHostOverUtilized(PowerHost host) {
        return host.getUtilizationOfCpu() > T_UPPER;
    }

    public int getMigrations()    { return migrations;    }
    public int getSlaViolations() { return slaViolations; }
}
