package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G4Xeon3040;
import org.cloudbus.cloudsim.provisioners.*;
import java.util.*;

public class DataCenterConfig {

    public static final int NUM_HOSTS = 10;
    public static final int HOST_CPU  = 400;   // doubled from 200
    public static final int NUM_VMS   = 25;

    public static List<PowerHost> createHosts() {
        List<PowerHost> hosts = new ArrayList<>();
        for (int i = 0; i < NUM_HOSTS; i++) {
            List<Pe> pes = new ArrayList<>();
            // Give each host 4 PEs so it can comfortably hold multiple VMs
            for (int j = 0; j < 4; j++) {
                pes.add(new Pe(j, new PeProvisionerSimple(HOST_CPU))); 
            }
            
            hosts.add(new PowerHost(
                i,
                new RamProvisionerSimple(16384),
                new BwProvisionerSimple(100000),
                1_000_000,
                pes,
                new VmSchedulerTimeShared(pes), // Essential for VM sharing
                new PowerModelSpecPowerHpProLiantMl110G4Xeon3040()
            ));
        }
        return hosts;
    }

    // VMs now take a mips array so each scenario can set its own demands
    public static List<PowerVm> createVms(int brokerId, int[] mipsValues) {
        List<PowerVm> vms = new ArrayList<>();
        for (int i = 0; i < NUM_VMS; i++) {
            vms.add(new PowerVm(
                i, brokerId, mipsValues[i], 1,
                512, 1000, 10000, 1, "Xen",
                new CloudletSchedulerTimeShared(), 300
            ));
        }
        return vms;
    }
}
