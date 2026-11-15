package com.hostel.entity.converter;

import jakarta.persistence.AttributeConverter;

/**
 * Base JPA converter storing Java enums consistently as lowercase_snake_case in DB
 * while keeping Java enum identities intact for business logic and DTO serialization.
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected LowercaseEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return Enum.valueOf(enumType, dbData.trim().toUpperCase());
    }
}

