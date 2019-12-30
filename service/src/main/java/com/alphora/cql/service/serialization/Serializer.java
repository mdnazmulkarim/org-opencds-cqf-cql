package com.alphora.cql.service.serialization;

public interface Serializer {
    Object deserialize (String value);
    String serialize(Object value);
}