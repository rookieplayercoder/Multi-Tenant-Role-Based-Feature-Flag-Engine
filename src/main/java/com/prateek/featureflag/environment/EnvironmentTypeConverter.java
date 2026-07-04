package com.prateek.featureflag.environment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Translates between the Java-idiomatic uppercase {@link EnvironmentType}
 * enum constants and the lowercase string values enforced by the
 * {@code ck_environments_key} CHECK constraint in the database.
 * <p>
 * {@code autoApply = true} means Hibernate applies this converter to every
 * field of type {@link EnvironmentType} automatically, without needing
 * {@code @Convert} on each usage site.
 */
@Converter(autoApply = true)
public class EnvironmentTypeConverter implements AttributeConverter<EnvironmentType, String> {

    @Override
    public String convertToDatabaseColumn(EnvironmentType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public EnvironmentType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EnvironmentType.valueOf(dbData.toUpperCase());
    }
}
