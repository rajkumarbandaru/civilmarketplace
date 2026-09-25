package com.civileng.marketplace.auth.controller;

import com.civileng.marketplace.auth.entity.User;
import com.civileng.marketplace.auth.entity.UserStatus;
import com.civileng.marketplace.auth.exception.UnauthenticatedException;
import com.civileng.marketplace.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalAccountControllerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final InternalAccountController controller = new InternalAccountController(users);

    @Test
    void findsAnActiveAccountByEmailForASignedInCallerOnly() {
        User anita = User.builder().id(11L).email("engineer@civileng.test").name("Anita").status(UserStatus.ACTIVE).build();
        when(users.findByEmailAndIsDeletedFalse("engineer@civileng.test")).thenReturn(Optional.of(anita));
        assertThat(controller.byEmail(10L, " Engineer@Civileng.test ").id()).isEqualTo(11L);
        assertThatThrownBy(() -> controller.byEmail(null, "engineer@civileng.test")).isInstanceOf(UnauthenticatedException.class);
        assertThatThrownBy(() -> controller.byEmail(10L, "nobody@civileng.test")).isInstanceOf(NoSuchElementException.class);
        anita.setStatus(UserStatus.SUSPENDED);
        assertThatThrownBy(() -> controller.byEmail(10L, "engineer@civileng.test")).isInstanceOf(NoSuchElementException.class);
    }
}
