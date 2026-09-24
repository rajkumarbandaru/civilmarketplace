package com.civileng.marketplace.user.service;

import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.user.dto.MediaDtos;
import com.civileng.marketplace.user.model.KycDocument;
import com.civileng.marketplace.user.model.WorkerPortfolio;
import com.civileng.marketplace.user.repository.KycDocumentRepository;
import com.civileng.marketplace.user.repository.UserProfileRepository;
import com.civileng.marketplace.user.repository.WorkerPortfolioRepository;
import com.civileng.marketplace.web.common.client.MediaRef;
import com.civileng.marketplace.web.common.client.MediaReferences;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KycAndPortfolioMediaTest {

    private final KycDocumentRepository kycRepo = mock(KycDocumentRepository.class);
    private final WorkerPortfolioRepository portfolioRepo = mock(WorkerPortfolioRepository.class);
    private final MediaReferences media = mock(MediaReferences.class);
    private final AuditPublisher audit = mock(AuditPublisher.class);
    private final KycService kyc = new KycService(kycRepo, mock(UserProfileRepository.class), audit, media);
    private final PortfolioService portfolio = new PortfolioService(portfolioRepo, media);

    private static final Instant SOON = Instant.parse("2026-09-24T10:05:00Z");
    private static final MediaRef PAN = new MediaRef("m1", "KYC_DOCUMENT", "PRIVATE", 7L, "pan.pdf",
            "application/pdf", 100L, "https://s3/signed-pan", SOON);

    {
        when(kycRepo.save(any())).thenAnswer(inv -> {
            KycDocument d = inv.getArgument(0);
            d.setId(11L);
            return d;
        });
        when(portfolioRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void kycStoresTheCheckedUploadIdNeverAUrl() {
        when(media.requireOwned("m1", "KYC_DOCUMENT", 7L)).thenReturn(PAN);
        KycDocument saved = kyc.submitDocument(7L,
                new MediaDtos.KycSubmitRequest(KycDocument.DocumentType.PAN, " ABCDE1234F ", "m1"));
        assertThat(saved.getMediaId()).isEqualTo("m1");
        assertThat(saved.getDocumentUrl()).isNull();
        assertThat(saved.getDocumentNumber()).isEqualTo("ABCDE1234F");
        assertThat(saved.getStatus()).isEqualTo(KycDocument.KycStatus.PENDING);
    }

    @Test
    void kycRefusesAnUploadTheCheckRejects() {
        when(media.requireOwned("m9", "KYC_DOCUMENT", 7L)).thenThrow(new IllegalArgumentException("Uploaded file not found"));
        assertThatThrownBy(() -> kyc.submitDocument(7L,
                new MediaDtos.KycSubmitRequest(KycDocument.DocumentType.PAN, null, "m9")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(kycRepo, never()).save(any());
    }

    @Test
    void ownerGetsAFreshSignedLinkAndStrangersGetNotFound() {
        KycDocument doc = KycDocument.builder().id(11L).userId(7L).mediaId("m1").build();
        when(kycRepo.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(doc));
        when(media.fresh("m1", 7L)).thenReturn(PAN);

        MediaDtos.FileLink link = kyc.ownFileLink(11L, 7L);
        assertThat(link.url()).isEqualTo("https://s3/signed-pan");
        assertThat(link.expiresAt()).isEqualTo(SOON);
        assertThatThrownBy(() -> kyc.ownFileLink(11L, 8L)).hasMessage("KYC document not found");
    }

    @Test
    void reviewerLinkIsAuditedAndLegacyRowsFallBackToTheirUrl() {
        KycDocument legacy = KycDocument.builder().id(3L).userId(7L).documentUrl("https://old/doc.jpg").build();
        when(kycRepo.findById(3L)).thenReturn(Optional.of(legacy));
        assertThat(kyc.reviewerFileLink(3L, 1L, "ADMIN").url()).isEqualTo("https://old/doc.jpg");
        verify(audit).publish(any());
        verifyNoInteractions(media);
    }

    @Test
    void portfolioKeepsThePublicUrlOfAVerifiedPhoto() {
        when(media.requireOwned("p1", "PORTFOLIO", 7L)).thenReturn(new MediaRef("p1", "PORTFOLIO", "PUBLIC", 7L,
                "slab.jpg", "image/jpeg", 10L, "http://cdn/slab.jpg", null));
        WorkerPortfolio item = portfolio.add(7L, new MediaDtos.PortfolioRequest(" Roof slab ", "", "Masonry", null, "p1"));
        assertThat(item.getImageUrl()).isEqualTo("http://cdn/slab.jpg");
        assertThat(item.getMediaId()).isEqualTo("p1");
        assertThat(item.getTitle()).isEqualTo("Roof slab");
        assertThat(item.getDescription()).isNull();
    }

    @Test
    void portfolioIsCappedAndDeletesAreOwnerOnly() {
        when(portfolioRepo.countByUserId(7L)).thenReturn(50L);
        assertThatThrownBy(() -> portfolio.add(7L, new MediaDtos.PortfolioRequest("t", null, null, null, "p1")))
                .hasMessageContaining("up to 50");
        verifyNoInteractions(media);

        when(portfolioRepo.findById(4L)).thenReturn(Optional.of(WorkerPortfolio.builder().id(4L).userId(7L).build()));
        assertThatThrownBy(() -> portfolio.delete(8L, 4L)).hasMessage("Portfolio item not found");
        portfolio.delete(7L, 4L);
        verify(portfolioRepo).delete(any());
    }
}
