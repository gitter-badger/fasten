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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package eu.fasten.analyzer.javacgwala.lapp.core;

import com.ibm.wala.types.Selector;

import java.util.jar.JarFile;

public class ResolvedMethod extends Method {

    public static final AnalysisContext DEFAULT_CONTEXT = new DefaultAnalysisContext();

    public final JarFile artifact;

    ResolvedMethod(String namespace, Selector symbol, JarFile artifact) {
        super(namespace, symbol);

        this.artifact = artifact;
    }

    public String toID() {
        return toID(namespace, symbol, artifact);
    }

    public static String toID(String namespace, Selector symbol, JarFile artifact) {
        return artifact == null ? "Unknown" : artifact.getName() + "::" + namespace + "." + symbol.toString();
    }

    public static ResolvedMethod findOrCreate(String namespace, Selector symbol, JarFile artifact) {
        return DEFAULT_CONTEXT.makeResolved(namespace, symbol, artifact);
    }
}
