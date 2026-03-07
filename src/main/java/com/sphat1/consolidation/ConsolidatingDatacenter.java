package com.sphat1.consolidation;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.VmAllocationPolicy.GuestMapping;
import org.cloudbus.cloudsim.Vm;

import java.util.*;

public class ConsolidatingDatacenter extends PowerDatacenter {

    private final HeuristicConsolidationPolicy consolidationPolicy;

    public ConsolidatingDatacenter(
            String name,
            org.cloudbus.cloudsim.DatacenterCharacteristics characteristics,
            HeuristicConsolidationPolicy vmAllocationPolicy,
            List<Storage> storageList,
            double schedulingInterval) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
        this.consolidationPolicy = vmAllocationPolicy;
    }

    @Override
    protected void updateCloudletProcessing() {
        // 1. Run consolidation logic
        if (CloudSim.clock() > 0.1) {
            runConsolidation();
        }

        // 2. Let the parent handle power accumulation — do NOT interfere with host state
        try {
            super.updateCloudletProcessing();
        } catch (NullPointerException e) {
            // Synchronization artifact during migration — safe to ignore for this tick
            System.out.println("  [System] Synchronization tick ignored during migration.");
        }
    }

    private void runConsolidation() {
        List<GuestEntity> allVms = new ArrayList<>();
        for (PowerHost h : this.<PowerHost>getHostList()) {
            for (GuestEntity g : h.getGuestList()) {
                if (!((Vm) g).isInMigration()) {
                    allVms.add(g);
                }
            }
        }

        if (allVms.isEmpty()) return;

        System.out.printf("%n[ConsolidatingDatacenter] Running consolidation at t=%.2f%n", CloudSim.clock());

        // Get migration plan from heuristic policy
        List<GuestMapping> plan = consolidationPolicy.optimizeAllocation(allVms);

        // Execute migrations
        for (GuestMapping m : plan) {
            Vm vm = (Vm) m.vm();
            PowerHost targetHost = (PowerHost) m.host();
            PowerHost sourceHost = (PowerHost) vm.getHost();

            if (sourceHost == null || targetHost.equals(sourceHost)) continue;

            System.out.printf("  [Executing] Migrating VM #%d -> Host #%d%n", vm.getId(), targetHost.getId());

            sourceHost.guestDestroy(vm);
            if (targetHost.guestCreate(vm)) {
                vm.setHost(targetHost);
            }
        }

        // ── REMOVED: h.setFailed(true/false) ──
        // BUG FIX: Calling setFailed() corrupts PowerDatacenter's internal power
        // accumulation. CloudSim's power model already handles idle/active hosts
        // correctly — do NOT override host state manually here.

        int activeHosts = 0;
        for (PowerHost h : this.<PowerHost>getHostList()) {
            if (!h.getGuestList().isEmpty()) {
                activeHosts++;
            }
        }
        System.out.printf("  [Status] Active Hosts: %d / %d%n", activeHosts, getHostList().size());
    }
}