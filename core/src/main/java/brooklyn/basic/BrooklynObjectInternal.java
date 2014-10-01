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
package brooklyn.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.DeferredSupplier;

import com.google.common.annotations.Beta;

public interface BrooklynObjectInternal extends BrooklynObject, Rebindable {
    
    ConfigurationSupportInternal config();
    
    @Beta
    public static interface ConfigurationSupportInternal extends BrooklynObject.ConfigurationSupport {
        
        /**
         * @return a read-only copy of all the config key/value pairs on this entity.
         */
        @Beta
        Map<ConfigKey<?>,Object> getAllConfig();
    
        /**
         * Returns a read-only view of all the config key/value pairs on this entity, backed by a string-based map, 
         * including config names that did not match anything on this entity.
         */
        @Beta
        ConfigBag getAllConfigBag();
    
        /**
         * Returns a read-only view of the local (i.e. not inherited) config key/value pairs on this entity, 
         * backed by a string-based map, including config names that did not match anything on this entity.
         */
        @Beta
        ConfigBag getLocalConfigBag();
        
        @Beta
        <T> T setConfig(ConfigKey<T> key, DeferredSupplier<T> val);
        
        @Beta
        <T> T setConfig(HasConfigKey<T> key, DeferredSupplier<T> val);
        
        @Beta
        void refreshInheritedConfig(BrooklynObjectInternal parent);
    }
}
