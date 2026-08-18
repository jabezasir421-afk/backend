package com.bluecollar.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = IndianPincodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface IndianPincode {

    String message() default "Pincode must be a valid 6-digit Indian PIN code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
