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
package org.apache.brooklyn.policy.enricher;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.mgmt.SubscriptionContext;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RollingMeanEnricherTest {
    
    AbstractApplication app;
    
    EntityLocal producer;

    Sensor<Integer> intSensor;
    AttributeSensor<Integer> deltaSensor;
    AttributeSensor<Double> avgSensor;
    RollingMeanEnricher<Integer> averager;

    @BeforeMethod(alwaysRun=true)
    public void before() {
        app = new AbstractApplication() {};
        producer = new AbstractEntity(app) {};
        producer.setParent(app);
        Entities.startManagement(app);

        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor");
        deltaSensor = new BasicAttributeSensor<Integer>(Integer.class, "delta sensor");
        avgSensor = new BasicAttributeSensor<Double>(Double.class, "avg sensor");
        
        producer.enrichers().add(new DeltaEnricher<Integer>(producer, intSensor, deltaSensor));
        averager = new RollingMeanEnricher<Integer>(producer, deltaSensor, avgSensor, 4);
        producer.enrichers().add(averager);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testDefaultAverage() {
        assertEquals(averager.getAverage(), null);
    }
    
    @Test
    public void testZeroWindowSize() {
        averager = new RollingMeanEnricher<Integer>(producer, deltaSensor, avgSensor, 0);
        producer.enrichers().add(averager);
        
        averager.onEvent(intSensor.newEvent(producer, 10));
        assertEquals(averager.getAverage(), null);
    }
    
    @Test
    public void testSingleValueAverage() {
        averager.onEvent(intSensor.newEvent(producer, 10));
        assertEquals(averager.getAverage(), 10d);
    }
    
    @Test
    public void testMultipleValueAverage() {
        averager.onEvent(intSensor.newEvent(producer, 10));
        averager.onEvent(intSensor.newEvent(producer, 20));
        averager.onEvent(intSensor.newEvent(producer, 30));
        averager.onEvent(intSensor.newEvent(producer, 40));
        assertEquals(averager.getAverage(), (10+20+30+40)/4d);
    }
    
    @Test
    public void testWindowSizeCulling() {
        averager.onEvent(intSensor.newEvent(producer, 10));
        averager.onEvent(intSensor.newEvent(producer, 20));
        averager.onEvent(intSensor.newEvent(producer, 30));
        averager.onEvent(intSensor.newEvent(producer, 40));
        averager.onEvent(intSensor.newEvent(producer, 50));
        averager.onEvent(intSensor.newEvent(producer, 60));
        assertEquals(averager.getAverage(), (30+40+50+60)/4d);
    }
}