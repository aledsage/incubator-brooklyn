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
package brooklyn.location.basic;

import java.util.Collection;
import java.util.Iterator;

import brooklyn.util.flags.TypeCoercions;

/**
 * @deprecated since 0.7.0; see {@link brooklyn.util.net.PortRanges}
 */
@Deprecated
public class PortRanges extends brooklyn.util.net.PortRanges {

    /**
     * previously performed the language extensions required for this project; now done in {@link TypeCoercions}
     * @deprecated since 0.7.0; no longer needed
     */
    public static void init() {
    }
    
    public static brooklyn.location.PortRange fromInteger(int x) {
        return new LegacyPortRange(brooklyn.util.net.PortRanges.fromInteger(x));
    }
    
    public static brooklyn.location.PortRange fromCollection(Collection<?> c) {
        return new LegacyPortRange(brooklyn.util.net.PortRanges.fromCollection(c));
    }

    public static brooklyn.location.PortRange fromString(String s) {
        return new LegacyPortRange(brooklyn.util.net.PortRanges.fromString(s));
    }
    
    private static class LegacyPortRange implements brooklyn.location.PortRange {
        private brooklyn.util.net.PortRange delegate;

        LegacyPortRange(brooklyn.util.net.PortRange range) {
            this.delegate = range;
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean asBoolean() {
            return delegate.asBoolean();
        }

        @Override
        public Iterator<Integer> iterator() {
            return delegate.iterator();
        }
    }
}
