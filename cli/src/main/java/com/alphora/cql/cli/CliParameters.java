package com.alphora.cql.cli;

import com.alphora.cql.service.EvaluationParameters;
import com.alphora.cql.service.ServiceParameters;
import com.alphora.cql.measure.MeasureParameters;

public class CliParameters {
    public ServiceParameters serviceParameters = new ServiceParameters();
    public EvaluationParameters evaluationParameters = new EvaluationParameters();
    public MeasureParameters measureParameters = new MeasureParameters();
}