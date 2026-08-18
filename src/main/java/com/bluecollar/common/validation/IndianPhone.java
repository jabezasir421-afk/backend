package com.bluecollar.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = IndianPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface IndianPhone {

    String message() default "Phone number must be a valid Indian mobile number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
