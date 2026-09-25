package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.model.MemberRole;
import com.civileng.marketplace.procurement.model.OrgMember;
import com.civileng.marketplace.procurement.repository.OrgMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static com.civileng.marketplace.procurement.service.Fixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotifierTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final OrgMemberRepository members = mock(OrgMemberRepository.class);
    private final Notifier notifier = new Notifier(members, kafka);

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void approversOnlyNeverTheActorAndOnlyAfterCommit() {
        OrgMember pending = member(1, 0, MemberRole.APPROVER);
        pending.setUserId(null);
        pending.setEmail("new@example.com");
        when(members.findByOrganizationIdOrderByIdAsc(1L)).thenReturn(List.of(
                member(1, 10, MemberRole.OWNER), member(1, 11, MemberRole.APPROVER), member(1, 12, MemberRole.MEMBER), pending));

        TransactionSynchronizationManager.initSynchronization();
        notifier.toOrg(1L, Notifier.APPROVERS, 10L, "T", "title", "msg", "PURCHASE_ORDER", 9L, "/procurement/orders/9");
        verifyNoInteractions(kafka);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Notifier.TOPIC), eq("PURCHASE_ORDER:9"), sent.capture());
        Notifier.Notice notice = (Notifier.Notice) sent.getValue();
        assertThat(notice.recipients()).containsExactly(new Notifier.Recipient(11L, "u11@example.com"),
                new Notifier.Recipient(null, "new@example.com"));
        assertThat(notice.link()).isEqualTo("/procurement/orders/9");
    }

    @Test
    void nobodyToTellMeansNothingSent() {
        when(members.findByOrganizationIdOrderByIdAsc(1L)).thenReturn(List.of(member(1, 10, MemberRole.OWNER)));
        notifier.toOrg(1L, Notifier.EVERYONE, 10L, "T", "t", "m", "RFQ", 1L, "/x");
        verifyNoInteractions(kafka);
    }
}
