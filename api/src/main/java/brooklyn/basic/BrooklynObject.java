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

import java.util.Set;

import javax.annotation.Nonnull;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.trait.Identifiable;
import brooklyn.management.Task;
import brooklyn.util.guava.Maybe;

import com.google.common.collect.ImmutableMap;

/**
 * Super-type of entity, location, policy and enricher.
 */
public interface BrooklynObject extends Identifiable {
    
    /**
     * A display name; recommended to be a concise single-line description.
     */
    String getDisplayName();

    BrooklynType getType();
    
    /** 
     * Tags are arbitrary objects which can be attached to an entity for subsequent reference.
     * They must not be null (as {@link ImmutableMap} may be used under the covers; also there is little point!);
     * and they should be amenable to our persistence (on-disk serialization) and our JSON serialization in the REST API.
     */
    TagSupport getTagSupport();
    
    ConfigurationSupport config();
    
    public static interface TagSupport {
        /**
         * @return An immutable copy of the set of tags on this entity. 
         * Note {@link #containsTag(Object)} will be more efficient,
         * and {@link #addTag(Object)} and {@link #removeTag(Object)} will not work on the returned set.
         */
        @Nonnull Set<Object> getTags();
        
        boolean containsTag(@Nonnull Object tag);
        
        boolean addTag(@Nonnull Object tag);
        
        boolean addTags(@Nonnull Iterable<?> tags);
        
        boolean removeTag(@Nonnull Object tag);
    }

    public static interface ConfigurationSupport {
        // TODO This is a copy-paste!

        // FIXME From location: deprecate/delete
//        /** True iff the indication config key is set, either inherited (second argument true) or locally-only (second argument false) */
//        boolean hasConfig(ConfigKey<?> key, boolean includeInherited);
//
//        /** Returns all config set, either inherited (argument true) or locally-only (argument false) */
//        public Map<String,Object> getAllConfig(boolean includeInherited);

        /**
         * Gets the given configuration value for this entity, in the following order of preference:
         * <li> value (including null) explicitly set on the entity
         * <li> value (including null) explicitly set on an ancestor (inherited)
         * <li> a default value (including null) on the best equivalent static key of the same name declared on the entity
         *      (where best equivalence is defined as preferring a config key which extends another, 
         *      as computed in EntityDynamicType.getConfigKeys)
         * <li> a default value (including null) on the key itself
         * <li> null
         */
        <T> T getConfig(ConfigKey<T> key);
        <T> T getConfig(HasConfigKey<T> key);

        /**
         * Returns the uncoerced value for this config key as set on this entity, if available,
         * not following any inheritance chains and not taking any default.
         */
        Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited);
        Maybe<Object> getConfigRaw(HasConfigKey<?> key, boolean includeInherited);
        
        /**
         * Must be called before the entity is managed.
         */
        <T> T setConfig(ConfigKey<T> key, T val);
        
        /**
         * Sets the config to the value returned by the task.
         * 
         * Returns immediately without blocking; subsequent calls to {@link #getConfig(ConfigKey)} 
         * will execute the task, and block until the task completes. 
         * 
         * @see {@link #setConfig(ConfigKey, Object)}
         */
        <T> T setConfig(ConfigKey<T> key, Task<T> val);
        
        /**
         * @see {@link #setConfig(ConfigKey, Object)}
         */
        <T> T setConfig(HasConfigKey<T> key, T val);
        
        /**
         * @see {@link #setConfig(ConfigKey, Task)}
         */
        <T> T setConfig(HasConfigKey<T> key, Task<T> val);
    }
}
