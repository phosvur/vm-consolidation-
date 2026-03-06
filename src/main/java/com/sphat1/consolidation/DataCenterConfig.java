package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G4Xeon3040;
import org.cloudbus.cloudsim.provisioners.*;
import java.util.*;

public class DataCenterConfig {

    public static final int NUM_HOSTS_HEURISTIC = 15;
    public static final int NUM_HOSTS_BASELINE  = 15;
    public static final int NUM_VMS             = 25;

    public static List<PowerHost> createHosts(int count) {
        List<PowerHost> hosts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Pe> pes = new ArrayList<>();
            pes.add(new Pe(0, new PeProvisionerSimple(400)));
            hosts.add(new PowerHost(
                i,
                new RamProvisionerSimple(16384),
                new BwProvisionerSimple(100000),
                1_000_000,
                pes,
                new VmSchedulerTimeShared(pes),
                new PowerModelSpecPowerHpProLiantMl110G4Xeon3040()
            ));
        }
        return hosts;
    }

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