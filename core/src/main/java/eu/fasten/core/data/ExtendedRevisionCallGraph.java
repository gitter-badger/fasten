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

package eu.fasten.core.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedRevisionCallGraph extends RevisionCallGraph {

    private static Logger logger = LoggerFactory.getLogger(ExtendedRevisionCallGraph.class);

    /**
     * For each class in the revision, class hierarchy keeps a {@link Type} that is accessible by
     * the {@link FastenURI} of the class as a key.
     * @implNote each method in the revision has a unique id in this CHA.
     */
    private final Map<FastenURI, Type> classHierarchy;

    /** Includes all the edges of the revision call graph (internal & external). */
    private final Graph graph;

    /** Keeps the name of call graph generator that generated this revision call graph. */
    private final String cgGenerator;

    /**
     * Creates {@link ExtendedRevisionCallGraph} with the given data.
     * @param forge          the forge.
     * @param product        the product.
     * @param version        the version.
     * @param timestamp      the timestamp (in seconds from UNIX epoch); optional: if not present,
     *                       it is set to -1.
     * @param cgGenerator    The name of call graph generator that generated this call graph.
     * @param depset         the depset.
     * @param classHierarchy class hierarchy of this revision including all classes of the revision
     *                       <code> Map<{@link FastenURI}, {@link Type}> </code>
     * @param graph          the call graph (no control is done on the graph) {@link Graph}
     *                       including internal and external calls of the revision.
     */
    public ExtendedRevisionCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, final String cgGenerator,
                                     final List<List<Dependency>> depset,
                                     final Map<FastenURI, Type> classHierarchy,
                                     final Graph graph) {

        super(forge, product, version, timestamp, depset, new ArrayList<>());
        this.cgGenerator = cgGenerator;
        this.classHierarchy = classHierarchy;
        this.graph = graph;
    }

    private ExtendedRevisionCallGraph(final ExtendedBuilder builder) {
        super(builder.forge, builder.product, builder.version, builder.timestamp, builder.depset,
            new ArrayList<>());
        this.cgGenerator = builder.cgGenerator;
        this.classHierarchy = builder.classHierarchy;
        this.graph = builder.graph;
    }

    /**
     * Creates {@link ExtendedRevisionCallGraph} for the given JSONObject.
     * @param json JSONObject of a revision call graph.
     */
    public ExtendedRevisionCallGraph(final JSONObject json) throws JSONException {

        super(json.getString("forge"),
            json.getString("product"),
            json.getString("version"),
            getTimeStamp(json),
            Dependency.depset(json.getJSONArray("depset")),
            new ArrayList<>());

        this.cgGenerator = json.getString("generator");
        this.graph = new Graph(json.getJSONObject("graph"));
        this.classHierarchy = getCHAFromJSON(json.getJSONObject("cha"));

    }

    /** If timestamp is present in the JSON set it otherwise set it to -1. */
    private static long getTimeStamp(JSONObject json) {
        try {
            return json.getLong("timestamp");
        } catch (final JSONException exception) {
            logger.warn("No timestamp provided: assuming -1");
            return -1;
        }
    }

    /**
     * Creates builder to build {@link ExtendedRevisionCallGraph}.
     * @return created builder
     */
    public static ExtendedBuilder extendedBuilder() {
        return new ExtendedBuilder();
    }

    /**
     * Creates a class hierarchy for the given JSONObject.
     * @param cha JSONObject of a cha.
     */
    public static Map<FastenURI, Type> getCHAFromJSON(final JSONObject cha) {

        final Map<FastenURI, Type> result = new HashMap<>();

        for (final var key : cha.keySet()) {
            result.put(FastenURI.create(key), new Type(cha.getJSONObject(key)));
        }
        return result;
    }

    /**
     * Produces the JSON representation of this {@link ExtendedRevisionCallGraph}.
     * @return the JSON representation.
     */
    @Override
    public JSONObject toJSON() {

        final var result = new JSONObject();
        result.put("forge", forge);
        result.put("product", product);
        result.put("version", version);
        result.put("generator", cgGenerator);
        if (timestamp >= 0) {
            result.put("timestamp", timestamp);
        }
        result.put("cha", toJSON(classHierarchy));
        result.put("depset", Dependency.toJSON(depset));
        result.put("graph", graph.toJSON());

        return result;
    }

    /**
     * Produces the JSON representation of class hierarchy.
     * @return the JSON representation.
     */
    public JSONObject toJSON(final Map<FastenURI, ExtendedRevisionCallGraph.Type> cha) {

        final var result = new JSONObject();

        for (final var entry : cha.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toJSON());
        }

        return result;

    }

    /**
     * Sorts the internal calls of this revision call graph.
     * @implNote since we use {@link Set} in the process of computing the internal calls and
     *     sets doesn't keep the order it might be needed to sort the edges
     */
    public void sortInternalCalls() {
        final var sortedList = new ArrayList<>(this.graph.internalCalls);
        sortedList.sort(Comparator.comparing(o -> (o.get(0).toString() + o.get(1))));
        this.graph.internalCalls.clear();
        this.graph.internalCalls.addAll(sortedList);
    }

    /**
     * Returns the map of all the methods of this object.
     * @return a Map of method ids and their corresponding {@link FastenURI}
     */
    public Map<Integer, FastenURI> mapOfAllMethods() {
        Map<Integer, FastenURI> result = new HashMap<>();
        for (final var aClass : this.getClassHierarchy().entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        return result;
    }

    public boolean isCallGraphEmpty() {
        return this.graph.internalCalls.isEmpty() && this.graph.externalCalls.isEmpty();
    }

    public String getCgGenerator() {
        return cgGenerator;
    }

    public Map<FastenURI, Type> getClassHierarchy() {
        return classHierarchy;
    }

    public Graph getGraph() {
        return graph;
    }

    /**
     * Builder to build {@link ExtendedRevisionCallGraph}.
     */
    public static final class ExtendedBuilder {
        private String forge;
        private String product;
        private String version;
        private String cgGenerator;
        private long timestamp;
        private List<List<Dependency>> depset = Collections.emptyList();
        private Map<FastenURI, Type> classHierarchy;
        private Graph graph;

        private ExtendedBuilder() {
        }

        public ExtendedBuilder forge(final String forge) {
            this.forge = forge;
            return this;
        }

        public ExtendedBuilder product(final String product) {
            this.product = product;
            return this;
        }

        public ExtendedBuilder version(final String version) {
            this.version = version;
            return this;
        }

        public ExtendedBuilder cgGenerator(final String cgGenerator) {
            this.cgGenerator = cgGenerator;
            return this;
        }

        public ExtendedBuilder timestamp(final long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ExtendedBuilder depset(final List<List<Dependency>> depset) {
            this.depset = depset;
            return this;
        }

        public ExtendedBuilder graph(final ExtendedRevisionCallGraph.Graph graph) {
            this.graph = graph;
            return this;
        }

        public ExtendedBuilder classHierarchy(final Map<FastenURI, Type> cha) {
            this.classHierarchy = cha;
            return this;
        }

        public ExtendedRevisionCallGraph build() {
            return new ExtendedRevisionCallGraph(this);
        }
    }


    public static class Graph {

        /**
         * It keeps all the internal calls of the call graph using the ids of source and target
         * method. First element of the int[] is the id of the source method and the second one is
         * the target's id. Ids are available in the class hierarchy.
         */
        private final List<List<Integer>> internalCalls;

        /**
         * External calls of the graph and key value metadata about each call. The {@link Pair}
         * keeps the id of source method in the left element and the {@link FastenURI} of the target
         * method in the right element. The meta data per call is stored as a map that keys and
         * values are {@link String}. For example in case of java for each call it can keep
         * (typeOfCall -> number_of_occurrence).
         */
        private final Map<Pair<Integer, FastenURI>, Map<String, String>> externalCalls;

        public Graph(final List<List<Integer>> internalCalls,
                     final Map<Pair<Integer, FastenURI>, Map<String, String>> externalCalls) {
            this.internalCalls = internalCalls;
            this.externalCalls = externalCalls;
        }

        /**
         * Creates {@link Graph} for the given JSONObject.
         * @param graph JSONObject of a graph including its internal calls and external calls.
         */
        public Graph(final JSONObject graph) {

            final var internalCalls = graph.getJSONArray("internalCalls");
            this.internalCalls = new ArrayList<>();
            final int numberOfArcs = internalCalls.length();
            for (int i = 0; i < numberOfArcs; i++) {
                final var pair = internalCalls.getJSONArray(i);
                this.internalCalls.add(Arrays.asList((Integer) pair.get(0), (Integer) pair.get(1)));
            }

            final var externalCalls = graph.getJSONArray("externalCalls");
            this.externalCalls = new HashMap<>();
            final int numberOfExternalArcs = externalCalls.length();
            for (int i = 0; i < numberOfExternalArcs; i++) {
                final var call = externalCalls.getJSONArray(i);
                final var callTypeJson = call.getJSONObject(2);
                final Map<String, String> callType = new HashMap<>();
                for (final var type : callTypeJson.keySet()) {
                    final String number = callTypeJson.getString(type);
                    callType.put(type, number);
                }
                this.externalCalls.put(new MutablePair<>(Integer.parseInt(call.getString(0)),
                    FastenURI.create(call.getString(1))), callType);
            }
        }

        /**
         * Converts a {@link Graph} object to its JSON representation.
         * @param graph the {@link Graph} object to be converted.
         * @return the corresponding JSON representation.
         */
        public JSONObject toJSON(final Graph graph) {

            final var result = new JSONObject();
            final var internalCallsJSON = new JSONArray();
            for (final var entry : graph.internalCalls) {
                internalCallsJSON.put(entry);
            }
            final var externalCallsJSON = new JSONArray();
            for (final var entry : graph.externalCalls.entrySet()) {
                final var call = new JSONArray();
                call.put(entry.getKey().getKey().toString());
                call.put(entry.getKey().getValue().toString());
                call.put(new JSONObject(entry.getValue()));
                externalCallsJSON.put(call);
            }

            result.put("internalCalls", internalCallsJSON);
            result.put("externalCalls", externalCallsJSON);
            return result;
        }

        public JSONObject toJSON() {
            return toJSON(this);
        }

        public List<List<Integer>> getInternalCalls() {
            return internalCalls;
        }

        public Map<Pair<Integer, FastenURI>, Map<String, String>> getExternalCalls() {
            return externalCalls;
        }

        public int size() {
            return internalCalls.size() + externalCalls.size();
        }

    }

    /** Each type is a class or an interface. */
    public static class Type {

        /** The source file name of this type. */
        private final String sourceFileName;

        /** Methods of this type and their unique ids (unique within the same artifact). */
        private final Map<Integer, FastenURI> methods;

        /** Classes that this type inherits from in the order of instantiation. */
        private final LinkedList<FastenURI> superClasses;

        /** Interfaces that this type or its super classes implement. */
        private final List<FastenURI> superInterfaces;

        /**
         * Creates {@link Type} for the given data.
         * @param sourceFile      the name of this type's source file
         * @param methods         a map of methods in this type with their indexed by their ids.
         * @param superClasses    classes that this type extends.
         * @param superInterfaces interfaces that this type implements.
         */
        public Type(final String sourceFile, final Map<Integer, FastenURI> methods,
                    final LinkedList<FastenURI> superClasses,
                    final List<FastenURI> superInterfaces) {
            this.sourceFileName = sourceFile;
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
        }

        /**
         * Creates {@link Type} for the given JSONObject.
         * @param type JSONObject of a type including its source file name, map of methods, super
         *             classes and super interfaces.
         */
        public Type(final JSONObject type) {

            this.sourceFileName = type.getString("sourceFile");

            final var methodsJson = type.getJSONObject("methods");
            this.methods = new HashMap<>();
            for (final var methodKey : methodsJson.keySet()) {
                this.methods.put(Integer.parseInt(methodKey),
                    FastenURI.create(methodsJson.getString(methodKey)));
            }

            final var superClassesJSON = type.getJSONArray("superClasses");
            this.superClasses = new LinkedList<>();
            final int numberOfSuperClasses = superClassesJSON.length();
            for (int i = 0; i < numberOfSuperClasses; i++) {
                this.superClasses.add(FastenURI.create(superClassesJSON.getString(i)));
            }

            final var superInterfacesJSON = type.getJSONArray("superInterfaces");
            this.superInterfaces = new ArrayList<>();
            final int numberOfSuperInterfaces = superInterfacesJSON.length();
            for (int i = 0; i < numberOfSuperInterfaces; i++) {
                this.superInterfaces.add(FastenURI.create(superInterfacesJSON.getString(i)));
            }

        }

        /** Converts all the valuses of a given Map to String. */
        public static <T> Map<Integer, String> toMapOfString(final Map<Integer, T> map) {
            final Map<Integer, String> methods = new HashMap<>();
            for (final var entry : map.entrySet()) {
                methods.put(entry.getKey(), entry.getValue().toString());
            }
            return methods;
        }

        /** Converts elements of a given list to String. */
        public static List<String> toListOfString(final List<?> list) {
            final List<String> result = new ArrayList<>();
            for (final var fastenURI : list) {
                result.add(fastenURI.toString());
            }
            return result;
        }

        /**
         * Converts a {@link Type} object to its JSON representation.
         * @param type the {@link Type} object to be converted.
         * @return the corresponding JSON representation.
         */
        public JSONObject toJSON(final Type type) {

            final var result = new JSONObject();

            result.put("methods", toMapOfString(type.methods));
            result.put("superClasses", toListOfString(type.superClasses));
            result.put("superInterfaces", toListOfString(type.superInterfaces));
            result.put("sourceFile", type.sourceFileName);

            return result;
        }

        public JSONObject toJSON() {
            return toJSON(this);
        }

        public String getSourceFileName() {
            return sourceFileName;
        }

        public Map<Integer, FastenURI> getMethods() {
            return this.methods;
        }

        public LinkedList<FastenURI> getSuperClasses() {
            return superClasses;
        }

        public List<FastenURI> getSuperInterfaces() {
            return superInterfaces;
        }

        @Override public String toString() {
            return "Type{"
                + "sourceFileName='" + sourceFileName + '\''
                + ", methods=" + methods
                + ", superClasses=" + superClasses
                + ", superInterfaces=" + superInterfaces
                + '}';
        }
    }

}
