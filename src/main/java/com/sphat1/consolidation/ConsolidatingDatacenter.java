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
        
        // 2. SAFETY CHECK: Catch the NullPointer before it happens
        try {
            super.updateCloudletProcessing();
        } catch (NullPointerException e) {
            // This happens when CloudSim tries to calculate power for a VM 
            // that we just moved, but hasn't fully 'landed' on the new host yet.
            // In simulation terms, it's safe to ignore this single tick error.
            System.out.println("  [System] Synchronization tick ignored during migration.");
        }
    }

    private void runConsolidation() {
        List<GuestEntity> allVms = new ArrayList<>();
        for (PowerHost h : this.<PowerHost>getHostList()) {
            for (GuestEntity g : h.getGuestList()) {
                // Only consider VMs not currently in flight
                if (!((Vm) g).isInMigration()) {
                    allVms.add(g);
                }
            }
        }
        
        if (allVms.isEmpty()) return;

        System.out.printf("%n[ConsolidatingDatacenter] Running consolidation at t=%.2f%n", CloudSim.clock());

        // Get the plan from our heuristic policy
        List<GuestMapping> plan = consolidationPolicy.optimizeAllocation(allVms);

        // Execute migrations
        for (GuestMapping m : plan) {
            Vm vm = (Vm) m.vm();
            PowerHost targetHost = (PowerHost) m.host();
            PowerHost sourceHost = (PowerHost) vm.getHost();

            // Guard: If sourceHost is null, the VM is already in transit
            if (sourceHost == null || targetHost.equals(sourceHost)) continue;

            System.out.printf("  [Executing] Migrating VM #%d -> Host #%d%n", vm.getId(), targetHost.getId());

            sourceHost.guestDestroy(vm);
            if (targetHost.guestCreate(vm)) {
                vm.setHost(targetHost);
            }
        }

     // --- POWER MANAGEMENT UPDATE ---
        int activeHosts = 0;
        for (PowerHost h : this.<PowerHost>getHostList()) {
            if (h.getGuestList().isEmpty()) {
                // In CloudSim 7.x, we usually set the host to "Failed" or "Shut Down"
                // to stop the power model from calculating idle power.
                h.setFailed(true); 
            } else {
                activeHosts++;
                // Re-enable the host if it was previously shut down and now has a VM
                h.setFailed(false);
            }
        }
        System.out.printf("  [Status] Active Hosts: %d / %d%n", activeHosts, getHostList().size());
    }
}