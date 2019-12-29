package com.alphora.cql.cli;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Map.Entry;

import com.alphora.cql.measure.MeasureLoader;
import com.alphora.cql.measure.MeasureParameters;
import com.alphora.cql.measure.common.MeasureEvaluation;
import com.alphora.cql.measure.common.MeasureReportType;
import com.alphora.cql.measure.r4.R4MeasureEvaluation;
import com.alphora.cql.measure.stu3.Stu3MeasureEvaluation;
import com.alphora.cql.service.EvaluationParameters;
import com.alphora.cql.service.Response;
import com.alphora.cql.service.Service;
import com.alphora.cql.service.ServiceFactory;
import com.alphora.cql.service.serialization.DefaultEvaluationResultsSerializer;
import com.alphora.cql.service.serialization.EvaluationResultsSerializer;

import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.cql.execution.LibraryResult;

import ca.uhn.fhir.context.FhirContext;

public class Main {

    public static void main(String[] args) {

        // TODO: Update cql engine dependencies
        disableAccessWarnings();

        CliParameters params = null;
        try {
            params = new ArgumentProcessor().parseAndConvert(args);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        ServiceFactory serviceFactory = new ServiceFactory(EnumSet.of(ServiceFactory.Options.EnableFileUri));
        Service service = serviceFactory.create(params.serviceParameters);

        try {
            if (params.measureParameters.measureName != null && !params.measureParameters.measureName.isEmpty()) {
                evaluateMeasure(service, params.measureParameters, params.evaluationParameters);
            }
            else {
                evaluateLibrary(service, params.serviceParameters.verbose, params.evaluationParameters);
            }
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

    }

    private static void evaluateMeasure(Service service, MeasureParameters measureParameters, EvaluationParameters evaluationParameters) {

        IBase measure = new MeasureLoader().loadMeasure(measureParameters.measurePath, measureParameters.measureName, measureParameters.measureVersion);

        MeasureEvaluation evaluation;
        // Set up 
        if (measure instanceof org.hl7.fhir.dstu3.model.Measure) {
            evaluation = new Stu3MeasureEvaluation<org.hl7.fhir.dstu3.model.Resource, org.hl7.fhir.dstu3.model.Patient>(
                service, (org.hl7.fhir.dstu3.model.Measure)measure, "org.hl7.fhir.dstu3.model", (x -> x.getIdElement().getIdPart()),
                evaluationParameters.contextParameters, evaluationParameters.parameters);

            IBase report = evaluation.evaluate(MeasureReportType.PATIENTLIST);
            System.out.println(FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString((IBaseResource)report));
        }
        else {
            evaluation = new R4MeasureEvaluation<org.hl7.fhir.r4.model.Resource, org.hl7.fhir.r4.model.Patient>(
                service, (org.hl7.fhir.r4.model.Measure)measure, "org.hl7.fhir.r4.model", (x -> x.getIdElement().getIdPart()),
                evaluationParameters.contextParameters, evaluationParameters.parameters);

            IBase report = evaluation.evaluate(MeasureReportType.SUBJECTLIST);
            System.out.println(FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString((IBaseResource)report));
        }
    }

    private static void evaluateLibrary(Service service, boolean verbose, EvaluationParameters evaluationParameters) {
        Response response  = service.evaluate(evaluationParameters);
        EvaluationResultsSerializer serializer;

        serializer = new DefaultEvaluationResultsSerializer();

        for (Entry<VersionedIdentifier, LibraryResult> libraryEntry : response.evaluationResult.libraryResults.entrySet()) {
            serializer.printResults(verbose, libraryEntry);
        }
    }

    @SuppressWarnings("unchecked")
    public static void disableAccessWarnings() {
        try {
            Class unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);

            Method putObjectVolatile = unsafeClass.getDeclaredMethod("putObjectVolatile", Object.class, long.class, Object.class);
            Method staticFieldOffset = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);

            Class loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field loggerField = loggerClass.getDeclaredField("logger");
            Long offset = (Long) staticFieldOffset.invoke(unsafe, loggerField);
            putObjectVolatile.invoke(unsafe, loggerClass, offset, null);
        } catch (Exception ignored) {
        }
    }

}