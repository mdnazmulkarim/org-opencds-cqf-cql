package com.alphora.cql.service;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

public class EvaluationParameters {
    public String libraryName;
    public String libraryVersion;

    // LibraryName, ExpressionName
    public List<Pair<String, String>> expressions;

    // LibraryName, ParameterName, Value
    // LibraryName may be null.
    public Map<Pair<String, String>,String> parameters;
    public Map<String,String> contextParameters;


}