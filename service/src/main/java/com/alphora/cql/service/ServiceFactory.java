package com.alphora.cql.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alphora.cql.service.factory.DataProviderFactory;
import com.alphora.cql.service.factory.DefaultDataProviderFactory;
import com.alphora.cql.service.factory.DefaultLibraryLoaderFactory;
import com.alphora.cql.service.factory.DefaultTerminologyProviderFactory;
import com.alphora.cql.service.factory.LibraryLoaderFactory;
import com.alphora.cql.service.factory.TerminologyProviderFactory;
import com.alphora.cql.service.resolver.DefaultParameterResolver;
import com.alphora.cql.service.resolver.ParameterResolver;
import com.alphora.cql.service.serialization.EvaluationResultsSerializer;

import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.UsingDef;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.execution.CqlEngine;
import org.opencds.cqf.cql.execution.EvaluationResult;
import org.opencds.cqf.cql.execution.LibraryLoader;
import org.opencds.cqf.cql.terminology.TerminologyProvider;

public class ServiceFactory {

    public enum Options {
        EnableFileUri
    }

    private EnumSet<Options> options;
    private EnumSet<org.opencds.cqf.cql.execution.CqlEngine.Options> engineOptions;
    private EnumSet<CqlTranslator.Options> translatorOptions;
    private TerminologyProviderFactory terminologyProviderFactory;
    private DataProviderFactory dataProviderFactory;
    private LibraryLoaderFactory libraryLoaderFactory;
    private ParameterResolver parameterResolver;

        // Use this as a quick way to enable file uris.
        public ServiceFactory() {
            this(null, null, null, null, EnumSet.noneOf(Options.class), null, null);
        }

    // Use this as a quick way to enable file uris.
    public ServiceFactory(EnumSet<Options> options) {
        this(null, null, null, null, options, null, null);
    }

    public ServiceFactory(LibraryLoaderFactory libraryLoaderFactory, DataProviderFactory dataProviderFactory,
            TerminologyProviderFactory terminologyProviderFactory, ParameterResolver parameterResolver,
            EnumSet<Options> options,
            EnumSet<org.opencds.cqf.cql.execution.CqlEngine.Options> engineOptions,
            EnumSet<CqlTranslator.Options> translatorOptions) {

        if (libraryLoaderFactory == null) {
            libraryLoaderFactory = new DefaultLibraryLoaderFactory();
        }

        if (dataProviderFactory == null) {
            dataProviderFactory = new DefaultDataProviderFactory();
        }

        if (terminologyProviderFactory == null) {
            terminologyProviderFactory = new DefaultTerminologyProviderFactory();
        }

        if (parameterResolver == null) {
           parameterResolver = new DefaultParameterResolver();
        }

        if (engineOptions == null) {
            engineOptions = EnumSet.of(org.opencds.cqf.cql.execution.CqlEngine.Options.EnableExpressionCaching);
        }

        if (options == null) {
            options = EnumSet.noneOf(Options.class);
        }

        if (translatorOptions == null) {
            // Default for measure eval
            translatorOptions =  EnumSet.of(
                CqlTranslator.Options.EnableAnnotations,
                CqlTranslator.Options.EnableLocators,
                CqlTranslator.Options.DisableListDemotion,
                CqlTranslator.Options.DisableListPromotion,
                CqlTranslator.Options.DisableMethodInvocation);
        }

        this.libraryLoaderFactory = libraryLoaderFactory;
        this.dataProviderFactory = dataProviderFactory;
        this.terminologyProviderFactory = terminologyProviderFactory;
        this.parameterResolver = parameterResolver;
        this.options = options;
        this.engineOptions = engineOptions;
        this.translatorOptions = translatorOptions;
    }

    public Service create(ServiceParameters serviceParameters) {
        validateParameters(serviceParameters);

        LibraryLoader libraryLoader = null;
        if (serviceParameters.libraryPath != null && !serviceParameters.libraryPath.isEmpty()) {
            libraryLoader = this.libraryLoaderFactory.create(serviceParameters.libraryPath, this.translatorOptions);
        }
        else {
            libraryLoader = this.libraryLoaderFactory.create(serviceParameters.libraries, this.translatorOptions);
        }

        Map<String, Pair<String, String>> modelVersionsAndUrls = this.getModelVersionAndUrls(serviceParameters.modelUris);
        TerminologyProvider terminologyProvider = this.terminologyProviderFactory.create(modelVersionsAndUrls, serviceParameters.terminologyUri);
        Map<String, DataProvider> dataProviders = this.dataProviderFactory.create(modelVersionsAndUrls, terminologyProvider);

        return new Service(libraryLoader, dataProviders, terminologyProvider, 
            this.parameterResolver, this.engineOptions);
    }

    private void ensureNotFileUri(String uri) {
        if (Helpers.isFileUri(uri)) {
            throw new IllegalArgumentException(String.format("%s is not a valid uri", uri));
        }
    }

    private void ensureNotFileUri(Collection<String> uris) {
        for (String s : uris) {
            ensureNotFileUri(s);
        }
    }

    private void validateParameters(ServiceParameters parameters) {
        // Ensure EnableFileURI option is respected. This is a potential security risk on a public server, so this must remain implemented.
        if (!this.options.contains(Options.EnableFileUri)) {
            if (parameters.libraryPath != null) {
                ensureNotFileUri(parameters.libraryPath);
            }

            if (parameters.terminologyUri != null) {
                ensureNotFileUri(parameters.terminologyUri);
            }

            if (parameters.modelUris != null) {
                ensureNotFileUri(parameters.modelUris.values());
            }
        }

        if ((parameters.libraries != null && !parameters.libraries.isEmpty()) && (parameters.libraryPath != null && !parameters.libraryPath.isEmpty())) {
            throw new IllegalArgumentException("libraries and library path are mutually exclusive. Only specify one.");
        }
    }

    private Map<String, Pair<String, String>> getModelVersionAndUrls(Map<String, String> modelUris) {
        final Map<String, String> shorthandMap = new HashMap<String, String>() {
            {
                put("FHIR", "http://hl7.org/fhir");
                put("QUICK", "http://hl7.org/fhir");
                put("QDM", "urn:healthit-gov:qdm:v5_4");
            }
        };

        if (modelUris == null) {
            return new HashMap<>();
        }

        Map<String, Pair<String, String>> versions = new HashMap<>();
        for (Map.Entry<String, String> modelUri : modelUris.entrySet()) {

            String[] modelAndVersion = modelUri.getKey().split(":");

            if ((modelAndVersion == null || modelAndVersion.length != 2) ||
               (modelAndVersion[0] == null || modelAndVersion[0].isEmpty() || modelAndVersion[1] == null || modelAndVersion[1].isEmpty())) {
                throw new IllegalArgumentException(String.format("Invalid model parameter: %s. Use the form model:version=url (e.g. FHIR:3.0.0=path/to/resources", modelUri.getKey()));
            }

            String model = modelAndVersion[0];
            String version = modelAndVersion[1];

            String uri = shorthandMap.containsKey(model) ? shorthandMap.get(model)
                    : modelUri.getKey();


            if (versions.containsKey(uri)) {
                if (!versions.get(uri).getKey().equals(version)) {
                    throw new IllegalArgumentException(String.format(
                            "Libraries are using multiple versions of %s. Only one version is supported at a time.",
                            modelUri.getKey()));
                }

            } else {
                versions.put(uri, Pair.of(version, modelUri.getValue()));
            }
        }

        return versions;
    }
}