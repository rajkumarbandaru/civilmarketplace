package com.civileng.marketplace.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The duplicate-account guard is only as strong as this normalisation: every variant a user
 * might type has to collapse to one canonical string, or the "already registered?" check
 * simply misses and a second account is created.
 */
class AccountIdentifiersTest {

    private final AccountIdentifiers identifiers = new AccountIdentifiers();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(identifiers, "defaultCountryCode", "+91");
    }

    @Test
    void allPhoneVariantsOfOneNumberCollapseToTheSameValue() {
        String canonical = "+919493564235";
        assertEquals(canonical, identifiers.normalisePhone("9493564235"));
        assertEquals(canonical, identifiers.normalisePhone("+919493564235"));
        assertEquals(canonical, identifiers.normalisePhone("+91 94935 64235"));
        assertEquals(canonical, identifiers.normalisePhone("09493564235"));
        assertEquals(canonical, identifiers.normalisePhone("0091 9493564235"));
        assertEquals(canonical, identifiers.normalisePhone("(94935) 64235"));
        assertEquals(canonical, identifiers.normalisePhone("+91-94935-64235"));
    }

    @Test
    void distinctNumbersStayDistinct() {
        assertEquals("+919493564235", identifiers.normalisePhone("9493564235"));
        assertEquals("+919493564236", identifiers.normalisePhone("9493564236"));
    }

    @Test
    void emailCaseAndPaddingDoNotCreateASecondAccount() {
        assertEquals("ravi@example.com", identifiers.normaliseEmail("Ravi@Example.COM"));
        assertEquals("ravi@example.com", identifiers.normaliseEmail("  ravi@example.com  "));
    }

    @Test
    void nullsPassThroughForTheValidationLayerToReject() {
        assertNull(identifiers.normalisePhone(null));
        assertNull(identifiers.normaliseEmail(null));
    }

    @Test
    void unusableInputIsLeftAloneRatherThanTurnedIntoAFakeNumber() {
        // Returning "+91" here would let two junk submissions collide as "duplicates".
        assertEquals("abc", identifiers.normalisePhone("abc"));
    }
}
