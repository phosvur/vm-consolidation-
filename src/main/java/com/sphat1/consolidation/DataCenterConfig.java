package com.sphat1.consolidation;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.power.*;
//import org.cloudbus.cloudsim.power.models.PowerModelSpecPower;
import org.cloudbus.cloudsim.power.models.PowerModelSpecPowerIbmX3250XeonX3470;
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
            PowerHost host = new PowerHost(
            	    i,
            	    new RamProvisionerSimple(2048),
            	    new BwProvisionerSimple(10000),
            	    1_000_000,
            	    pes,
            	    new VmSchedulerTimeShared(pes),
            	    new PowerModelSpecPowerIbmX3250XeonX3470() // built‑in power model
            	);
            	hosts.add(host);

        }
        return hosts;
    }

    public static List<PowerVm> createVms(int brokerId) {
        List<PowerVm> vms = new ArrayList<>();
        Random rand = new Random(42);
        for (int i = 0; i < NUM_VMS; i++) {
            int mips = 15 + rand.nextInt(51); // 15–65 units
            vms.add(new PowerVm(
                i, brokerId, mips, 1,
                512, 1000, 10000, 1, "Xen",
                new CloudletSchedulerTimeShared(), 300
            ));
        }
        return vms;
    }
}