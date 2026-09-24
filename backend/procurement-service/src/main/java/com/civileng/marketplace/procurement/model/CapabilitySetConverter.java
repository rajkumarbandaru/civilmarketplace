package com.civileng.marketplace.procurement.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class CapabilitySetConverter implements AttributeConverter<Set<Capability>, String> {

    @Override
    public String convertToDatabaseColumn(Set<Capability> attribute) {
        return attribute == null ? "" : attribute.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<Capability> convertToEntityAttribute(String dbData) {
        Set<Capability> set = EnumSet.noneOf(Capability.class);
        if (dbData != null && !dbData.isBlank()) {
            Arrays.stream(dbData.split(",")).map(String::trim).map(Capability::valueOf).forEach(set::add);
        }
        return set;
    }
}
