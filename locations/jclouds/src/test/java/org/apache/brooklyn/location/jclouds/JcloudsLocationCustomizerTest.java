/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.location.jclouds;

import java.util.List;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocationCustomizer;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.location.jclouds.JcloudsLocationTest.FakeLocalhostWithParentJcloudsLocation;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 */
public class JcloudsLocationCustomizerTest implements JcloudsLocationConfig {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(JcloudsLocationCustomizerTest.class);
    
    private LocalManagementContext managementContext;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        StaticRecordingJcloudsLocationCustomizer.calls.clear();
        managementContext = LocalManagementContextForTests.newInstance(BrooklynProperties.Factory.builderEmpty().build());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearUp() throws Exception {
        try {
            if (managementContext != null) Entities.destroyAll(managementContext);
        } finally {
            StaticRecordingJcloudsLocationCustomizer.calls.clear();
        }
    }

    // TODO fails because can't obtain throws Exception; really need to re-write JcloudLocation to make
    // it more testable!
    @Test(groups="WIP")
    public void testFoo() throws Exception {
//        JcloudsLocationCustomizer customizer = setup.get(JCLOUDS_LOCATION_CUSTOMIZER);
//        Collection<JcloudsLocationCustomizer> customizers = setup.get(JCLOUDS_LOCATION_CUSTOMIZERS);
//        @SuppressWarnings("deprecation")
//        String customizerType = setup.get(JCLOUDS_LOCATION_CUSTOMIZER_TYPE);
//        @SuppressWarnings("deprecation")
//        String customizersSupplierType = setup.get(JCLOUDS_LOCATION_CUSTOMIZERS_SUPPLIER_TYPE);

        StaticRecordingJcloudsLocationCustomizer customizer = new StaticRecordingJcloudsLocationCustomizer();
        BailOutJcloudsLocation jcl = BailOutJcloudsLocation.newBailOutJcloudsLocation(managementContext, ImmutableMap.<ConfigKey<?>, Object>of(
                JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS, ImmutableList.of(customizer)));

        try {
            jcl.obtain();
        } finally {
            System.out.println(StaticRecordingJcloudsLocationCustomizer.calls);
        }
    }

    // TODO This mostly just tests how FakeLocalhostWithParentJcloudsLocation is implemented.
    // We really want a way to test more of JcloudsLocaition, without overriding obtain 
    // (because doing that changes almost all the behaviour of the class under test!)
    @Test
    public void testInvokesCustomizerCallbacks() throws Exception {
        JcloudsLocationCustomizer customizer = Mockito.mock(JcloudsLocationCustomizer.class);
        MachineLocationCustomizer machineCustomizer = Mockito.mock(MachineLocationCustomizer.class);
//        Mockito.when(customizer.customize(Mockito.any(JcloudsLocation.class), Mockito.any(ComputeService.class), Mockito.any(JcloudsSshMachineLocation.class)));
        ConfigBag allConfig = ConfigBag.newInstance()
            .configure(CLOUD_PROVIDER, "aws-ec2")
            .configure(ACCESS_IDENTITY, "bogus")
            .configure(ACCESS_CREDENTIAL, "bogus")
            .configure(JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS, ImmutableList.of(customizer))
            .configure(JcloudsLocation.MACHINE_LOCATION_CUSTOMIZERS, ImmutableList.of(machineCustomizer))
            .configure(MACHINE_CREATE_ATTEMPTS, 1);
        FakeLocalhostWithParentJcloudsLocation ll = managementContext.getLocationManager().createLocation(LocationSpec.create(FakeLocalhostWithParentJcloudsLocation.class).configure(allConfig.getAllConfig()));
        JcloudsMachineLocation l = (JcloudsMachineLocation)ll.obtain();
        Mockito.verify(customizer, Mockito.times(1)).customize(ll, null, l);
        Mockito.verify(customizer, Mockito.never()).preRelease(l);
        Mockito.verify(customizer, Mockito.never()).postRelease(l);
        Mockito.verify(machineCustomizer, Mockito.times(1)).customize(l);
        Mockito.verify(machineCustomizer, Mockito.never()).preRelease(l);
        
        ll.release(l);
        Mockito.verify(customizer, Mockito.times(1)).preRelease(l);
        Mockito.verify(customizer, Mockito.times(1)).postRelease(l);
        Mockito.verify(machineCustomizer, Mockito.times(1)).preRelease(l);
    }
    
    public static class StaticRecordingJcloudsLocationCustomizer extends BasicJcloudsLocationCustomizer {
        
        private static final Logger log = LoggerFactory.getLogger(StaticRecordingJcloudsLocationCustomizer.class);

        public static class RecordedCall {
            public final String method;
            public final List<Object> args;
            
            public RecordedCall(String method, Iterable<? extends Object> args) {
                this.method = method;
                this.args = ImmutableList.copyOf(args);
            }
            
            @Override
            public String toString() {
                return method+"("+Joiner.on(", ").join(args)+")";
            }
        }
        
        static final List<RecordedCall> calls = Lists.newCopyOnWriteArrayList();
        
        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
            RecordedCall call = new RecordedCall("customize-templateBuilder", ImmutableList.of(location, computeService, templateBuilder));
            log.info(call.toString());
            calls.add(call);
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, Template template) {
            RecordedCall call = new RecordedCall("customize-template", ImmutableList.of(location, computeService, template));
            log.info(call.toString());
            calls.add(call);
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
            RecordedCall call = new RecordedCall("customize-templateOptions", ImmutableList.of(location, computeService, templateOptions));
            log.info(call.toString());
            calls.add(call);
        }

        @Override
        public void customize(JcloudsLocation location, ComputeService computeService, JcloudsMachineLocation machine) {
            RecordedCall call = new RecordedCall("customize-machine", ImmutableList.of(location, computeService, machine));
            log.info(call.toString());
            calls.add(call);
        }
        
        @Override
        public void preRelease(JcloudsMachineLocation machine) {
            RecordedCall call = new RecordedCall("preRelease", ImmutableList.of(machine));
            log.info(call.toString());
            calls.add(call);
        }

        @Override
        public void postRelease(JcloudsMachineLocation machine) {
            RecordedCall call = new RecordedCall("postRelease", ImmutableList.of(machine));
            log.info(call.toString());
            calls.add(call);
        }
    }
}
