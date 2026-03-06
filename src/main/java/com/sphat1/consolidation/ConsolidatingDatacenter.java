package com.sphat1.consolidation;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
//import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.VmAllocationPolicy.GuestMapping;
import org.cloudbus.cloudsim.core.CloudActionTags;

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
        // Let super run first — this processes cloudlets and advances the clock.
        // After super completes, VMs that have finished are already deallocated,
        // so consolidation only sees live VMs.
        super.updateCloudletProcessing();

        if (CloudSim.clock() > 0.1) {
            runConsolidation();
        }
    }

    private void runConsolidation() {
        List<GuestEntity> allVms = new ArrayList<>();
        for (PowerHost h : this.<PowerHost>getHostList()) {
            for (GuestEntity g : h.getGuestList()) {
                Vm vm = (Vm) g;
                if (vm.getHost() == null || vm.isInMigration()) continue;
                allVms.add(g);
            }
        }
        if (allVms.isEmpty()) return;

        System.out.printf("%n[ConsolidatingDatacenter] Running consolidation at t=%.2f with %d VMs%n",
            CloudSim.clock(), allVms.size());

        consolidationPolicy.setExternalCallAllowed(true);
        List<GuestMapping> plan = consolidationPolicy.optimizeAllocation(allVms);
        consolidationPolicy.setExternalCallAllowed(false);

        int executed = 0;
        for (GuestMapping m : plan) {
            Vm vm                = (Vm) m.vm();
            PowerHost targetHost = (PowerHost) m.host();

            if (vm.getHost() == null) {
                System.out.printf("  [Skipped] VM #%d already destroyed%n", vm.getId());
                continue;
            }

            PowerHost sourceHost = (PowerHost) vm.getHost();
            if (targetHost.equals(sourceHost)) continue;

            System.out.printf("  [Executing] Migrating VM #%d Host #%d -> Host #%d%n",
                vm.getId(), sourceHost.getId(), targetHost.getId());

            // Use CloudSim's built-in migration path so it properly
            // handles cloudlet scheduler handoff between hosts.
            Map<String, Object> migrate = new HashMap<>();
            migrate.put("vm", vm);
            migrate.put("host", targetHost);
            sendNow(getId(), CloudActionTags.VM_MIGRATE, migrate);
            executed++;
        }
        System.out.printf("  [Consolidation] Queued %d migrations%n", executed);
    }
}