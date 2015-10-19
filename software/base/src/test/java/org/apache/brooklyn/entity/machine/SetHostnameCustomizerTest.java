package org.apache.brooklyn.entity.machine;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class SetHostnameCustomizerTest extends BrooklynAppUnitTestSupport {

    private SetHostnameCustomizer customizer;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        customizer = new SetHostnameCustomizer(ConfigBag.newInstance());
    }
    
    @Test
    public void testGeneratedHostnameUsesPrivateIp() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("privateAddresses", ImmutableList.of("1.2.3.4", "5.6.7.8"))
                .configure("address", "4.3.2.1"));
        
        assertEquals(customizer.generateHostname(machine), "ip-1-2-3-4-"+machine.getId());
    }
    
    @Test
    public void testGeneratedHostnameUsesPublicIpIfNoPrivate() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "4.3.2.1"));
        
        assertEquals(customizer.generateHostname(machine), "ip-4-3-2-1-"+machine.getId());
    }
    
    @Test
    public void testGeneratedHostnameUsesPublicIpIfEmptyListOfPrivate() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("privateAddresses", ImmutableList.of())
                .configure("address", "4.3.2.1"));
        
        assertEquals(customizer.generateHostname(machine), "ip-4-3-2-1-"+machine.getId());
    }
}
