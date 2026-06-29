package com.bluecollar.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class IndianPhoneValidator implements ConstraintValidator<IndianPhone, String> {

    private static final Pattern INDIAN_PHONE_PATTERN = Pattern.compile("^(\\+91[6-9]\\d{9}|[6-9]\\d{9})$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return INDIAN_PHONE_PATTERN.matcher(value.trim()).matches();
    }
}
