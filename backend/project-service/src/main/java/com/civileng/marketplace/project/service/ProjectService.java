package com.civileng.marketplace.project.service;

import com.civileng.marketplace.audit.common.AuditAction;
import com.civileng.marketplace.audit.common.AuditEventMessage;
import com.civileng.marketplace.audit.common.AuditPublisher;
import com.civileng.marketplace.project.client.BookingDto;
import com.civileng.marketplace.project.client.BookingServiceClient;
import com.civileng.marketplace.project.client.EscrowDto;
import com.civileng.marketplace.project.client.PaymentServiceClient;
import com.civileng.marketplace.project.dto.*;
import com.civileng.marketplace.project.exception.AccessDeniedException;
import com.civileng.marketplace.project.model.*;
import com.civileng.marketplace.project.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {

    private static final String SOURCE = "project-service";
    private static final String ENTITY = "Project";

    /** From auth-service's roles seed data. */
    private static final Set<String> ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "ADMIN", "SUB_ADMIN", "REGIONAL_ADMIN");

    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProjectDocumentRepository documentRepository;
    private final ProjectStatusHistoryRepository statusHistoryRepository;
    private final BookingServiceClient bookingServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final AuditPublisher auditPublisher;

    // --------------------------------------------------------------------- projects

    @Transactional
    public Project createProject(Long ownerId, CreateProjectRequest request) {
        ProjectType type = parseType(request.getProjectType());
        validateDates(request.getStartDate(), request.getEndDate());

        Project project = Project.builder()
                .ownerId(ownerId)
                .name(request.getName())
                .description(request.getDescription())
                .projectType(type)
                .status(ProjectStatus.DRAFT)
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .budgetCeiling(request.getBudgetCeiling())
                .costCentre(request.getCostCentre())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        Project saved = projectRepository.save(project);
        statusHistoryRepository.save(ProjectStatusHistory.builder()
                .projectId(saved.getId())
                .fromStatus(null)
                .toStatus(ProjectStatus.DRAFT)
                .actorId(ownerId)
                .reason("Project created")
                .build());
        log.info("Project {} created by owner {}", saved.getId(), ownerId);

        audit(AuditAction.CREATE, ownerId, null, saved.getId(), ownerId,
                null, "status=DRAFT,name=" + saved.getName(), null);
        return saved;
    }

    @Transactional(readOnly = true)
    public Project getProject(Long projectId, Long actorId, String actorRole) {
        Project project = requireProject(projectId);
        requireViewer(project, actorId, actorRole);
        return project;
    }

    @Transactional(readOnly = true)
    public Page<Project> listMyProjects(Long ownerId, String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return projectRepository.findByOwnerIdAndIsDeletedFalseOrderByCreatedAtDesc(ownerId, pageable);
        }
        return projectRepository.findByOwnerIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
                ownerId, parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Project> listAllProjects(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return projectRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
        }
        return projectRepository.findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(
                parseStatus(status), pageable);
    }

    @Transactional
    public Project updateProject(Long projectId, Long actorId, UpdateProjectRequest request) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);
        if (project.getStatus().isTerminal()) {
            throw new IllegalArgumentException(
                    "A " + project.getStatus() + " project cannot be edited");
        }

        String before = "budgetCeiling=" + project.getBudgetCeiling()
                + ",name=" + project.getName();
        BigDecimal previousCeiling = project.getBudgetCeiling();

        if (request.getName() != null) project.setName(request.getName());
        if (request.getDescription() != null) project.setDescription(request.getDescription());
        if (request.getAddressLine() != null) project.setAddressLine(request.getAddressLine());
        if (request.getCity() != null) project.setCity(request.getCity());
        if (request.getState() != null) project.setState(request.getState());
        if (request.getPincode() != null) project.setPincode(request.getPincode());
        if (request.getCostCentre() != null) project.setCostCentre(request.getCostCentre());
        if (request.getStartDate() != null) project.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) project.setEndDate(request.getEndDate());
        if (request.getBudgetCeiling() != null) project.setBudgetCeiling(request.getBudgetCeiling());
        validateDates(project.getStartDate(), project.getEndDate());

        Project saved = projectRepository.save(project);

        // FR-08 warns everyone booked on the project when the ceiling moves. There is no
        // notification fan-out for it yet (announcements, item 7), so for now a ceiling change
        // that leaves milestones over-allocated is surfaced in the log and the summary's
        // overAllocated flag rather than pushed to those users.
        if (request.getBudgetCeiling() != null
                && !request.getBudgetCeiling().equals(previousCeiling)) {
            log.info("Project {} budget ceiling changed {} -> {} by {}",
                    projectId, previousCeiling, request.getBudgetCeiling(), actorId);
        }

        audit(AuditAction.UPDATE, actorId, null, projectId, project.getOwnerId(), before,
                "budgetCeiling=" + saved.getBudgetCeiling() + ",name=" + saved.getName(),
                request.getReason());
        return saved;
    }

    /**
     * FR-05 with the SRS's completion guard: a project cannot reach COMPLETED while any linked
     * booking is still in flight. When booking-service is unreachable the transition is refused
     * rather than allowed — see {@code BookingServiceClientFallbackFactory}.
     */
    @Transactional
    public Project changeStatus(Long projectId, Long actorId, StatusChangeRequest request) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);

        ProjectStatus target = parseStatus(request.getStatus());
        ProjectStatus current = project.getStatus();
        if (current == target) {
            throw new IllegalArgumentException("Project is already " + target);
        }
        if (current.isTerminal()) {
            throw new IllegalArgumentException(
                    "A " + current + " project cannot transition to " + target);
        }
        if (target == ProjectStatus.COMPLETED) {
            requireNoActiveBookings(projectId);
        }

        project.setStatus(target);
        Project saved = projectRepository.save(project);
        statusHistoryRepository.save(ProjectStatusHistory.builder()
                .projectId(projectId)
                .fromStatus(current)
                .toStatus(target)
                .actorId(actorId)
                .reason(request.getReason())
                .build());
        log.info("Project {} status {} -> {} by {}", projectId, current, target, actorId);

        audit(AuditAction.UPDATE, actorId, null, projectId, project.getOwnerId(),
                "status=" + current, "status=" + target, request.getReason());
        return saved;
    }

    /**
     * Soft delete. FR-09 blocks deletion while an EscrowHold or Dispute is open; escrow does not
     * exist yet (item 5), so the check that can be made today is the booking equivalent — any
     * non-terminal booking blocks it, and a DISPUTED booking is non-terminal by definition.
     */
    @Transactional
    public void deleteProject(Long projectId, Long actorId) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);
        requireNoActiveBookings(projectId);

        project.setIsDeleted(true);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
        log.info("Project {} soft-deleted by {}", projectId, actorId);

        audit(AuditAction.DELETE, actorId, null, projectId, project.getOwnerId(),
                "isDeleted=false", "isDeleted=true", null);
    }

    // --------------------------------------------------------------------- milestones

    @Transactional
    public Milestone addMilestone(Long projectId, Long actorId, MilestoneRequest request) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);

        Milestone milestone = Milestone.builder()
                .projectId(projectId)
                .title(request.getTitle())
                .description(request.getDescription())
                .targetDate(request.getTargetDate())
                .budgetAllocation(request.getBudgetAllocation())
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .completionState(MilestoneState.PENDING)
                .build();

        Milestone saved = milestoneRepository.save(milestone);
        warnIfOverAllocated(project, request.getOverrideReason(), actorId);
        log.info("Milestone {} added to project {} by {}", saved.getId(), projectId, actorId);
        return saved;
    }

    @Transactional
    public Milestone updateMilestone(Long projectId, Long milestoneId, Long actorId,
                                     MilestoneRequest request) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);
        Milestone milestone = requireMilestone(projectId, milestoneId);

        if (request.getTitle() != null) milestone.setTitle(request.getTitle());
        if (request.getDescription() != null) milestone.setDescription(request.getDescription());
        if (request.getTargetDate() != null) milestone.setTargetDate(request.getTargetDate());
        if (request.getBudgetAllocation() != null) {
            milestone.setBudgetAllocation(request.getBudgetAllocation());
        }
        if (request.getSortOrder() != null) milestone.setSortOrder(request.getSortOrder());

        Milestone saved = milestoneRepository.save(milestone);
        warnIfOverAllocated(project, request.getOverrideReason(), actorId);
        return saved;
    }

    @Transactional
    public Milestone changeMilestoneState(Long projectId, Long milestoneId, Long actorId,
                                          String state) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);
        Milestone milestone = requireMilestone(projectId, milestoneId);

        MilestoneState target = parseMilestoneState(state);
        milestone.setCompletionState(target);
        milestone.setCompletedAt(target == MilestoneState.COMPLETED ? LocalDateTime.now() : null);
        Milestone saved = milestoneRepository.save(milestone);
        log.info("Milestone {} on project {} -> {} by {}", milestoneId, projectId, target, actorId);
        return saved;
    }

    /** Soft delete only — a completed booking must keep its historical milestone reference. */
    @Transactional
    public void deleteMilestone(Long projectId, Long milestoneId, Long actorId) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);
        Milestone milestone = requireMilestone(projectId, milestoneId);

        milestone.setIsDeleted(true);
        milestone.setDeletedAt(LocalDateTime.now());
        milestoneRepository.save(milestone);
        log.info("Milestone {} on project {} soft-deleted by {}", milestoneId, projectId, actorId);
    }

    @Transactional(readOnly = true)
    public List<Milestone> listMilestones(Long projectId, Long actorId, String actorRole) {
        Project project = requireProject(projectId);
        requireViewer(project, actorId, actorRole);
        return milestoneRepository.findByProjectIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(projectId);
    }

    // --------------------------------------------------------------------- documents

    @Transactional
    public ProjectDocument attachDocument(Long projectId, Long actorId,
                                          AttachDocumentRequest request) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);

        ProjectDocument document = documentRepository.save(ProjectDocument.builder()
                .projectId(projectId)
                .fileRef(request.getFileRef())
                .docType(request.getDocType())
                .fileName(request.getFileName())
                .uploadedBy(actorId)
                .build());
        log.info("Document {} attached to project {} by {}", document.getId(), projectId, actorId);
        return document;
    }

    @Transactional(readOnly = true)
    public List<ProjectDocument> listDocuments(Long projectId, Long actorId, String actorRole) {
        Project project = requireProject(projectId);
        requireViewer(project, actorId, actorRole);
        return documentRepository.findByProjectIdAndIsDeletedFalseOrderByCreatedAtDesc(projectId);
    }

    @Transactional
    public void deleteDocument(Long projectId, Long documentId, Long actorId) {
        Project project = requireProject(projectId);
        requireOwner(project, actorId);
        ProjectDocument document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!document.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Document does not belong to this project");
        }
        document.setIsDeleted(true);
        documentRepository.save(document);
    }

    // --------------------------------------------------------------------- rollup

    @Transactional(readOnly = true)
    public ProjectSummary getSummary(Long projectId, Long actorId, String actorRole) {
        Project project = requireProject(projectId);
        requireViewer(project, actorId, actorRole);

        List<Milestone> milestones =
                milestoneRepository.findByProjectIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(projectId);

        BigDecimal allocated = milestones.stream()
                .map(Milestone::getBudgetAllocation)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long completed = milestones.stream()
                .filter(m -> m.getCompletionState() == MilestoneState.COMPLETED)
                .count();
        Integer percentComplete = milestones.isEmpty()
                ? null
                : (int) Math.round(100.0 * completed / milestones.size());

        List<BookingDto> bookings = bookingServiceClient.getProjectBookings(projectId);
        boolean bookingDataAvailable = bookings != null;
        BigDecimal spend = BigDecimal.ZERO;
        int bookingCount = 0;
        int activeCount = 0;
        if (bookingDataAvailable) {
            bookingCount = bookings.size();
            for (BookingDto booking : bookings) {
                spend = spend.add(booking.spendContribution());
                if (!booking.isTerminal()) activeCount++;
            }
        }

        List<EscrowDto> escrow = paymentServiceClient.getProjectEscrow(projectId);
        boolean escrowDataAvailable = escrow != null;
        BigDecimal escrowHeld = BigDecimal.ZERO;
        BigDecimal escrowReleased = BigDecimal.ZERO;
        int disputed = 0;
        if (escrowDataAvailable) {
            for (EscrowDto hold : escrow) {
                if (hold.isHeld()) {
                    escrowHeld = escrowHeld.add(
                            hold.getAmount() == null ? BigDecimal.ZERO : hold.getAmount());
                    if ("DISPUTED".equals(hold.getStatus())) disputed++;
                } else if (hold.isReleased()) {
                    escrowReleased = escrowReleased.add(
                            hold.getNetAmount() == null ? BigDecimal.ZERO : hold.getNetAmount());
                }
            }
        }

        BigDecimal ceiling = project.getBudgetCeiling();
        return ProjectSummary.builder()
                .project(project)
                .milestones(milestones)
                .budgetCeiling(ceiling)
                .allocatedBudget(allocated)
                .actualSpend(spend)
                .remainingBudget(ceiling == null ? null : ceiling.subtract(spend))
                .overAllocated(ceiling != null && allocated.compareTo(ceiling) > 0)
                .overBudget(ceiling != null && spend.compareTo(ceiling) > 0)
                .milestoneCount(milestones.size())
                .completedMilestoneCount((int) completed)
                .percentComplete(percentComplete)
                .bookingCount(bookingCount)
                .activeBookingCount(activeCount)
                .bookingDataAvailable(bookingDataAvailable)
                .escrowHeld(escrowHeld)
                .escrowReleased(escrowReleased)
                .escrowHoldCount(escrowDataAvailable ? escrow.size() : 0)
                .disputedEscrowCount(disputed)
                .escrowDataAvailable(escrowDataAvailable)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProjectStatusHistory> getStatusHistory(Long projectId, Long actorId,
                                                       String actorRole) {
        Project project = requireProject(projectId);
        requireViewer(project, actorId, actorRole);
        return statusHistoryRepository.findByProjectIdOrderByChangedAtAsc(projectId);
    }

    // --------------------------------------------------------------------- helpers

    private Project requireProject(Long projectId) {
        return projectRepository.findByIdAndIsDeletedFalse(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
    }

    private Milestone requireMilestone(Long projectId, Long milestoneId) {
        Milestone milestone = milestoneRepository.findByIdAndIsDeletedFalse(milestoneId)
                .orElseThrow(() -> new IllegalArgumentException("Milestone not found"));
        if (!milestone.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Milestone does not belong to this project");
        }
        return milestone;
    }

    private void requireOwner(Project project, Long actorId) {
        if (actorId == null || !actorId.equals(project.getOwnerId())) {
            throw new AccessDeniedException("Only the project owner may perform this action");
        }
    }

    /** Owner, or a staff role — Super Admin gets read access for dispute investigation. */
    private void requireViewer(Project project, Long actorId, String actorRole) {
        if (actorId != null && actorId.equals(project.getOwnerId())) return;
        if (actorRole != null && ADMIN_ROLES.contains(actorRole)) return;
        throw new AccessDeniedException("You do not have access to this project");
    }

    /**
     * The SRS makes over-allocation a soft warning rather than a hard block, on the grounds that
     * a Company mid-project knows more about its own budget than we do. The override reason is
     * logged so the decision is traceable afterwards.
     */
    private void warnIfOverAllocated(Project project, String overrideReason, Long actorId) {
        if (project.getBudgetCeiling() == null) return;
        BigDecimal allocated = milestoneRepository
                .findByProjectIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(project.getId()).stream()
                .map(Milestone::getBudgetAllocation)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(project.getBudgetCeiling()) > 0) {
            log.warn("Project {} milestone allocation {} exceeds ceiling {} (actor {}, reason: {})",
                    project.getId(), allocated, project.getBudgetCeiling(), actorId,
                    overrideReason == null ? "none given" : overrideReason);
            audit(AuditAction.UPDATE, actorId, null, project.getId(), project.getOwnerId(),
                    "allocation<=ceiling", "allocation=" + allocated
                            + ",ceiling=" + project.getBudgetCeiling(),
                    "Budget over-allocation override: "
                            + (overrideReason == null ? "no reason given" : overrideReason));
        }
    }

    private void requireNoActiveBookings(Long projectId) {
        List<BookingDto> bookings = bookingServiceClient.getProjectBookings(projectId);
        if (bookings == null) {
            throw new IllegalArgumentException(
                    "Cannot verify this project's bookings right now — try again shortly");
        }
        long active = bookings.stream().filter(b -> !b.isTerminal()).count();
        if (active > 0) {
            throw new IllegalArgumentException(
                    active + " booking(s) on this project are still in progress");
        }
    }

    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }

    private ProjectType parseType(String value) {
        try {
            return ProjectType.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown project type: " + value);
        }
    }

    private ProjectStatus parseStatus(String value) {
        try {
            return ProjectStatus.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown project status: " + value);
        }
    }

    private MilestoneState parseMilestoneState(String value) {
        try {
            return MilestoneState.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown milestone state: " + value);
        }
    }

    private void audit(AuditAction action, Long actorId, String actorRole, Long entityId,
                       Long subjectUserId, String before, String after, String reason) {
        auditPublisher.publish(AuditEventMessage.builder()
                .sourceService(SOURCE)
                .actorId(actorId)
                .actorRole(actorRole)
                .action(action)
                .entityType(ENTITY)
                .entityId(entityId == null ? null : String.valueOf(entityId))
                .subjectUserId(subjectUserId)
                .beforeState(before)
                .afterState(after)
                .reason(reason)
                .build());
    }
}
