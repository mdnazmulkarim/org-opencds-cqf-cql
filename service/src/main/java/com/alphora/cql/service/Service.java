package com.alphora.cql.service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alphora.cql.service.factory.DataProviderFactory;
import com.alphora.cql.service.factory.TerminologyProviderFactory;
import com.alphora.cql.service.resolver.ParameterResolver;

import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.UsingDef;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.execution.CqlEngine;
import org.opencds.cqf.cql.execution.EvaluationResult;
import org.opencds.cqf.cql.execution.LibraryLoader;
import org.opencds.cqf.cql.execution.CqlEngine.Options;
import org.opencds.cqf.cql.terminology.TerminologyProvider;

public class Service {

    // TODO: The parameter resolver will eventually use meta-data from the libraries
    // in order to construct / validate the execution parameters (For example, knowing
    // that the parameter type is a FHIR resource, so it needs to use a FHIR deserializer
    // to correctly instantiate the parameter as an object.
    private ParameterResolver parameterResolver;
    private LibraryLoader libraryLoader;
    private EnumSet<Options> engineOptions;

    private Map<String, DataProvider> dataProviders;
    private TerminologyProvider terminologyProvider;

    public Service(LibraryLoader libraryLoader,  Map<String, DataProvider> dataProviders, TerminologyProvider terminologyProvider, 
        ParameterResolver parameterResolver, EnumSet<Options> engineOptions) {
        this.parameterResolver = parameterResolver;
        this.libraryLoader = libraryLoader;
        this.dataProviders = dataProviders;
        this.terminologyProvider = terminologyProvider;
        this.engineOptions = engineOptions;
    }

    private void validateParameters(EvaluationParameters parameters) {
        if (parameters.libraryName == null && (parameters.expressions == null || parameters.expressions.isEmpty())) {
            throw new IllegalArgumentException("libraryName or expressions must be specified.");
        }

        if (parameters.libraryName != null && (parameters.expressions != null && !parameters.expressions.isEmpty())) {
            throw new IllegalArgumentException("libraryName and expressions are mutually exclusive. Only specify one.");
        }
    }

    private Map<VersionedIdentifier, Set<String>> toExpressionMap(List<Pair<String, String>> expressions) {
        Map<VersionedIdentifier, Set<String>> map = new HashMap<>();
        for (Pair<String, String> p : expressions) {
            VersionedIdentifier vi = toExecutionIdentifier(p.getLeft(), null);
            if (!map.containsKey(vi)) {
                map.put(vi, new HashSet<>());
            }

            map.get(vi).add(p.getRight());
        }

        return map;
    }

    private Map<VersionedIdentifier, Map<String, String>> toParameterMap(Map<Pair<String, String>,String> parameters) {
        Map<VersionedIdentifier, Map<String, String>> map = new HashMap<>();
        for (Map.Entry<Pair<String, String>, String> p : parameters.entrySet()) {
            VersionedIdentifier vi = toExecutionIdentifier(p.getKey().getLeft(), null);
            if (!map.containsKey(vi)) {
                map.put(vi, new HashMap<>());
            }

            map.get(vi).put(p.getKey().getRight(), p.getValue());
        }

        return map;
    }

    public Response evaluate(EvaluationParameters params) {
        validateParameters(params);

        Map<VersionedIdentifier, Library> libraries = new HashMap<VersionedIdentifier, Library>();
        if (params.libraryName != null) {
            Library lib = libraryLoader.load(toExecutionIdentifier(params.libraryName, params.libraryVersion));
            if (lib != null) {
                libraries.put(lib.getIdentifier(), lib);
            }
        }

        Map<String, Object> resolvedContextParameters = this.parameterResolver.resolvecontextParameters(params.contextParameters);
        Map<VersionedIdentifier, Map<String, String>> libraryParameters = this.toParameterMap(params.parameters);

        Map<VersionedIdentifier, Set<String>> expressions = this.toExpressionMap(params.expressions);
        for (VersionedIdentifier v : expressions.keySet()) {
            Library lib = libraryLoader.load(v);
            if (lib != null && !libraries.containsKey(lib.getIdentifier())) {
                libraries.put(lib.getIdentifier(), lib);
            }
        }

        for (VersionedIdentifier v : libraryParameters.keySet()) {
            Library lib = libraryLoader.load(v);
            if (lib != null && !libraries.containsKey(lib.getIdentifier())) {
                libraries.put(lib.getIdentifier(), lib);
            }
        }

        Map<VersionedIdentifier, Map<String, Object>> resolvedEvaluationParameters = this.parameterResolver.resolveParameters(libraries, libraryParameters);
        CqlEngine engine = new CqlEngine(this.libraryLoader, dataProviders, terminologyProvider, this.engineOptions);

        EvaluationResult result = null;
        if (params.libraryName != null) {
            result = engine.evaluate(resolvedContextParameters, resolvedEvaluationParameters,
                    this.toExecutionIdentifier(params.libraryName, null));
        } else {
            result = engine.evaluate(resolvedContextParameters, resolvedEvaluationParameters, expressions);
        }

        Response response = new Response();
        response.evaluationResult = result;

        return response;
    }

    public VersionedIdentifier toExecutionIdentifier(String name, String version) {
        return new VersionedIdentifier().withId(name).withVersion(version);
    }
}