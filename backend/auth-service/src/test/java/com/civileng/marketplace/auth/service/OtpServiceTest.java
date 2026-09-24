package com.civileng.marketplace.auth.service;

import com.civileng.marketplace.tenant.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OtpServiceTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    /** A tiny Redis: enough for set/get/increment/delete. */
    private final Map<String, Object> store = new HashMap<>();
    private OtpService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
                .when(values).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        when(values.get(anyString())).thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        when(values.increment(anyString())).thenAnswer(inv ->
                (Long) store.merge(inv.getArgument(0), 1L, (a, b) -> (Long) a + 1));
        when(redis.hasKey(anyString())).thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove(inv.<String>getArgument(0)) != null);
        when(redis.delete(anyCollection())).thenAnswer(inv -> {
            ((java.util.Collection<String>) inv.getArgument(0)).forEach(store::remove);
            return 1L;
        });
        service = new OtpService(redis);
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 5);
        ReflectionTestUtils.setField(service, "otpLength", 6);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 30);
        TenantContext.set("acme");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void aCodeIsOnlyGoodInTheWorkspaceItWasIssuedIn() {
        String code = service.generateAndStoreOtp("7:email");
        assertThat(store).containsKey("otp:acme:7:email");

        TenantContext.set("bhoomi");
        assertThat(service.validateOtp("7:email", code)).isFalse();

        TenantContext.set("acme");
        assertThat(service.validateOtp("7:email", code)).isTrue();
        assertThat(service.validateOtp("7:email", code)).as("single use").isFalse();
    }

    @Test
    void theCodeIsVoidedAfterFiveWrongGuesses() {
        String code = service.generateAndStoreOtp("7:email");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < OtpService.MAX_ATTEMPTS; i++) {
            assertThat(service.validateOtp("7:email", wrong)).isFalse();
        }
        assertThat(service.validateOtp("7:email", code)).as("right code, too late").isFalse();
    }

    @Test
    void aFreshCodeResetsTheAttemptCount() {
        service.generateAndStoreOtp("7:email");
        service.validateOtp("7:email", "x");
        store.remove("otp:cooldown:acme:7:email");
        String code = service.generateAndStoreOtp("7:email");
        assertThat(store).doesNotContainKey("otp:attempts:acme:7:email");
        assertThat(service.validateOtp("7:email", code)).isTrue();
    }

    @Test
    void masksWhatItLogs() {
        assertThat(OtpService.mask("asha@example.com")).isEqualTo("a***@example.com");
        assertThat(OtpService.mask("+919000000003")).isEqualTo("***03");
        assertThat(OtpService.mask("7:email")).isEqualTo("***il");
    }
}
