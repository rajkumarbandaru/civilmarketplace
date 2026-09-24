package com.civileng.marketplace.booking.service;

import com.civileng.marketplace.booking.event.BookingEventPublisher;
import com.civileng.marketplace.booking.model.Booking;
import com.civileng.marketplace.booking.repository.BookingRepository;
import com.civileng.marketplace.web.common.client.QuotaExceededException;
import com.civileng.marketplace.web.common.client.Quotas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingQuotaTest {

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final Quotas quotas = mock(Quotas.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<Quotas> provider = mock(ObjectProvider.class);
    private final BookingService service = new BookingService(bookings, mock(BookingEventPublisher.class), provider);

    @Test
    void theMonthlyLimitIsCheckedAgainstThisMonthsBookingsBeforeAnythingIsSaved() {
        when(provider.getIfAvailable()).thenReturn(quotas);
        when(bookings.countByCreatedAtGreaterThanEqual(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay()))
                .thenReturn(500L);
        doThrow(new QuotaExceededException("bookings.monthly", 500, "limit"))
                .when(quotas).require("bookings.monthly", 500L, "bookings a month");

        assertThatThrownBy(() -> service.createBooking(new Booking())).isInstanceOf(QuotaExceededException.class);
        verify(bookings, never()).save(any());
    }

    @Test
    void withoutEntitlementsWiredNothingIsChecked() {
        when(provider.getIfAvailable()).thenReturn(null);
        when(bookings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        try {
            service.createBooking(new Booking());
        } catch (RuntimeException ignored) {
            // Later steps of creation are not this test's concern.
        }
        verify(bookings, never()).countByCreatedAtGreaterThanEqual(any());
    }
}
