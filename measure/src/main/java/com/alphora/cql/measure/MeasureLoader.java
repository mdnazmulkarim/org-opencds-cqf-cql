package com.alphora.cql.measure;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.instance.model.api.IBase;

import ca.uhn.fhir.context.FhirContext;

// This is conceptually the same as library provider so
// there may be a way to reuse that code.
public class MeasureLoader {

    public IBase loadMeasure(String path, String name, String version) {
        // This is the connectathon convention measure-name-version.json
        String fullName = String.join("-", "measure", name, version) + ".json";
        Path measurePath = Path.of(path);
        File measure = measurePath.resolve(fullName).toFile();

        if (!measure.exists()) {
            throw new IllegalArgumentException(String.format("Measure not found. Searched %s", measure.getAbsolutePath()));
        }

        IBase measureResource = null;

        try {
            String content = new String (Files.readAllBytes(measure.toPath()));
            measureResource = FhirContext.forDstu3().newJsonParser().parseResource(new StringReader(content));
        }
        catch (Exception e) {
            try {
                String content = new String (Files.readAllBytes(measure.toPath()));
                measureResource = FhirContext.forR4().newJsonParser().parseResource(new StringReader(content));
            }
            catch (Exception e2) {
                throw new IllegalArgumentException(String.format("Unable to read Measure resource located at %s", measurePath.toString()));
            }
        }

        return measureResource;
    }

}