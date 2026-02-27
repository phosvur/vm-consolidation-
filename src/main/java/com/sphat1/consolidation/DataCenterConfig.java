package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G4Xeon3040;
import org.cloudbus.cloudsim.provisioners.*;
import java.util.*;

public class DataCenterConfig {

    public static final int NUM_HOSTS = 10;
    public static final int HOST_CPU  = 200;
    public static final int NUM_VMS   = 25;

    public static List<PowerHost> createHosts() {
        List<PowerHost> hosts = new ArrayList<>();
        for (int i = 0; i < NUM_HOSTS; i++) {
            List<Pe> pes = new ArrayList<>();
            pes.add(new Pe(0, new PeProvisionerSimple(HOST_CPU)));
            hosts.add(new PowerHost(
                i,
                new RamProvisionerSimple(16384),  // 16 GB — plenty of headroom for migrations
                new BwProvisionerSimple(100000),  // 100 Gbps — remove BW as bottleneck too
                1_000_000,
                pes,
                new VmSchedulerTimeShared(pes),
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