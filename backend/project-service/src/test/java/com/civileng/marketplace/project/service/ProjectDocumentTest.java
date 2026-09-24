package com.civileng.marketplace.project.service;

import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.project.client.ProjectBookingsClient;
import com.civileng.marketplace.project.client.ProjectEscrowClient;
import com.civileng.marketplace.project.dto.AttachDocumentRequest;
import com.civileng.marketplace.project.model.Project;
import com.civileng.marketplace.project.model.ProjectDocument;
import com.civileng.marketplace.project.repository.MilestoneRepository;
import com.civileng.marketplace.project.repository.ProjectDocumentRepository;
import com.civileng.marketplace.project.repository.ProjectRepository;
import com.civileng.marketplace.project.repository.ProjectStatusHistoryRepository;
import com.civileng.marketplace.web.common.client.MediaRef;
import com.civileng.marketplace.web.common.client.MediaReferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectDocumentTest {

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectDocumentRepository documents = mock(ProjectDocumentRepository.class);
    private final MediaReferences media = mock(MediaReferences.class);
    private ProjectService service;

    private static final MediaRef PLAN = new MediaRef("m1", "PROJECT_DOCUMENT", "PRIVATE", 7L,
            "site-plan.pdf", "application/pdf", 100L, "https://s3/signed", null);

    @BeforeEach
    void setUp() {
        service = new ProjectService(projects, mock(MilestoneRepository.class), documents,
                mock(ProjectStatusHistoryRepository.class), mock(ProjectBookingsClient.class),
                mock(ProjectEscrowClient.class), mock(AuditPublisher.class), media);
        Project project = new Project();
        project.setId(1L);
        project.setOwnerId(7L);
        when(projects.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(project));
        when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AttachDocumentRequest attach(String fileRef, String name) {
        AttachDocumentRequest r = new AttachDocumentRequest();
        r.setFileRef(fileRef);
        r.setDocType("DRAWING");
        r.setFileName(name);
        return r;
    }

    @Test
    void attachesOnlyTheOwnersVerifiedUploadAndDefaultsTheName() {
        when(media.requireOwned("m1", "PROJECT_DOCUMENT", 7L)).thenReturn(PLAN);
        ProjectDocument doc = service.attachDocument(1L, 7L, attach("m1", null));
        assertThat(doc.getFileRef()).isEqualTo("m1");
        assertThat(doc.getFileName()).isEqualTo("site-plan.pdf");
    }

    @Test
    void aFileTheCheckRefusesIsNotAttached() {
        when(media.requireOwned("x", "PROJECT_DOCUMENT", 7L)).thenThrow(new IllegalArgumentException("Uploaded file not found"));
        assertThatThrownBy(() -> service.attachDocument(1L, 7L, attach("x", "a")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(documents, never()).save(any());
    }

    @Test
    void onlyViewersGetALinkAndOnlyForThisProjectsDocuments() {
        ProjectDocument doc = ProjectDocument.builder().id(5L).projectId(1L).fileRef("m1").build();
        when(documents.findByIdAndIsDeletedFalse(5L)).thenReturn(Optional.of(doc));
        when(media.fresh("m1", 7L)).thenReturn(PLAN);
        when(media.fresh("m1", 2L)).thenReturn(PLAN);

        assertThat(service.getDocumentLink(1L, 5L, 7L, "CUSTOMER").url()).isEqualTo("https://s3/signed");
        assertThat(service.getDocumentLink(1L, 5L, 2L, "ADMIN").url()).isEqualTo("https://s3/signed");
        assertThatThrownBy(() -> service.getDocumentLink(1L, 5L, 9L, "CUSTOMER"))
                .hasMessageContaining("do not have access");

        ProjectDocument other = ProjectDocument.builder().id(6L).projectId(99L).fileRef("m2").build();
        when(documents.findByIdAndIsDeletedFalse(6L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.getDocumentLink(1L, 6L, 7L, "CUSTOMER")).hasMessage("Document not found");
        verify(media, never()).fresh(eq("m2"), any());
    }
}
