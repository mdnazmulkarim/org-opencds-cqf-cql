package com.alphora.cql.service.serialization;

import com.alphora.cql.service.EvaluationParameters;
import com.alphora.cql.service.Response;
import com.alphora.cql.service.Service;
import com.alphora.cql.service.ServiceFactory;
import com.alphora.cql.service.ServiceParameters;
import com.google.common.collect.Lists;

import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.elm.execution.VersionedIdentifier;

public class InterimBadCqlTypeSerializer implements Serializer {

    private static final String x = "library X\n define \"X\": %s";

    @Override
    public Object deserialize(String value) {
        String cqlText =  String.format(x, value);
        ServiceFactory factory = new ServiceFactory();
        ServiceParameters p = new ServiceParameters();
        p.libraries = Lists.newArrayList(cqlText);
        Service s = factory.create(p);
        EvaluationParameters ep = new EvaluationParameters();
        ep.expressions = Lists.newArrayList(Pair.of("X", "X"));
        Response r = s.evaluate(ep);
        return r.evaluationResult.forLibrary(new VersionedIdentifier().withId("X")).forExpression("X");
    }

    @Override
    public String serialize(Object value) {
        // TODO Auto-generated method stub
        return null;
    }

}