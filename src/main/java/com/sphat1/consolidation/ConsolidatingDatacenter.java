package com.sphat1.consolidation;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.VmAllocationPolicy.GuestMapping;

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
        // Run our consolidation FIRST, before the parent deallocates any VMs
        if (CloudSim.clock() > 0.1) {  // skip t=0 initialization
            runConsolidation();
        }
        // Then let the normal processing (which may deallocate VMs) proceed
        super.updateCloudletProcessing();
    }

    private void runConsolidation() {
        List<GuestEntity> allVms = new ArrayList<>();
        for (PowerHost h : this.<PowerHost>getHostList()) {
            allVms.addAll(h.getGuestList());
        }
        if (allVms.isEmpty()) return;

        System.out.printf("%n[ConsolidatingDatacenter] Running consolidation at t=%.2f with %d VMs%n",
            CloudSim.clock(), allVms.size());

        List<GuestMapping> plan = consolidationPolicy.optimizeAllocation(allVms);

        for (GuestMapping m : plan) {
            System.out.printf("  [Executing] Migrating VM #%d → Host #%d%n",
                ((org.cloudbus.cloudsim.Vm) m.vm()).getId(),
                m.host().getId());
            // Execute the migration using the parent datacenter's mechanism
            consolidationPolicy.deallocateHostForGuest(m.vm());
            consolidationPolicy.allocateHostForGuest(m.vm(), (PowerHost) m.host());
        }
    }
}