package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.ManagementContext;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindPolicyTest {

    // FIXME Add test about dependent configuration serialization?!
    
    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private TestApplication origApp;
    private File mementoDir;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
        origApp = new TestApplication();
        managementContext.manage(origApp);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Test
    public void testRestoresSimplePolicy() throws Exception {
        MyPolicy origPolicy = new MyPolicy(MutableMap.of("myfield", "myval"));
        origApp.addPolicy(origPolicy);
        
        TestApplication newApp = rebind();
        Collection<Policy> policies = newApp.getPolicies();
        MyPolicy newPolicy = (MyPolicy) Iterables.get(policies, 0);
        
        assertEquals(newPolicy.myfield, origPolicy.myfield);
    }

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
    
    public static class MyPolicy extends AbstractPolicy {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag
        String myfield;

        private final Object dummy = new Object(); // so not serializable
        
        public MyPolicy() {
        }
        
        public MyPolicy(Map flags) {
            super(flags);
        }
    }
}
