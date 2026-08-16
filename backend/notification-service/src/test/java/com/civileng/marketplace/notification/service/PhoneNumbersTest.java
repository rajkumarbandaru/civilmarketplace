package com.civileng.marketplace.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhoneNumbersTest {

    private final PhoneNumbers phoneNumbers = new PhoneNumbers();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(phoneNumbers, "defaultCountryCode", "+91");
    }

    @Test
    void appliesTheDefaultCallingCodeToNationalNumbers() {
        assertEquals("+919000000001", phoneNumbers.toE164("9000000001"));
    }

    @Test
    void keepsNumbersThatAreAlreadyE164() {
        assertEquals("+14155238886", phoneNumbers.toE164("+1 (415) 523-8886"));
    }

    @Test
    void convertsTheInternationalPrefix() {
        assertEquals("+919000000001", phoneNumbers.toE164("00919000000001"));
    }

    @Test
    void rejectsNumbersTooShortToDial() {
        assertNull(phoneNumbers.toE164("12345"));
        assertNull(phoneNumbers.toE164(""));
        assertNull(phoneNumbers.toE164(null));
    }

    @Test
    void masksAllButTheLastFourDigits() {
        assertEquals("*********0001", PhoneNumbers.mask("+919000000001"));
        assertEquals("****", PhoneNumbers.mask(null));
    }
}
