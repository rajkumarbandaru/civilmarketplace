package com.civileng.marketplace.user.controller;

import com.civileng.marketplace.user.model.MaterialItem;
import com.civileng.marketplace.user.model.SupplierMaterialPrice;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** What the "publish a rate" form sends must validate: the supplier is the caller, not a field. */
class SupplierMaterialPriceValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aRateWithoutASupplierIdIsAValidRequest() {
        SupplierMaterialPrice request = SupplierMaterialPrice.builder()
                .materialItem(MaterialItem.builder().id(2L).build())
                .price(new BigDecimal("395")).city("Hyderabad").brand("UltraTech").build();
        assertThat(validator.validate(request)).isEmpty();
    }
}
