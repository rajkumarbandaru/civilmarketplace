package com.civileng.marketplace.user.service;

import com.civileng.marketplace.user.dto.MediaDtos;
import com.civileng.marketplace.user.model.WorkerPortfolio;
import com.civileng.marketplace.user.repository.WorkerPortfolioRepository;
import com.civileng.marketplace.web.common.client.MediaRef;
import com.civileng.marketplace.web.common.client.MediaReferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Photos of past work on a worker's profile. Public by design: they are shown to customers. */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioService {

    static final String PURPOSE = "PORTFOLIO";
    static final int MAX_ITEMS = 50;

    private final WorkerPortfolioRepository repository;
    private final MediaReferences mediaReferences;

    @Transactional(readOnly = true)
    public List<WorkerPortfolio> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public WorkerPortfolio add(Long userId, MediaDtos.PortfolioRequest request) {
        if (repository.countByUserId(userId) >= MAX_ITEMS) {
            throw new IllegalArgumentException("A portfolio can hold up to " + MAX_ITEMS + " items");
        }
        MediaRef photo = mediaReferences.requireOwned(request.mediaId(), PURPOSE, userId);
        WorkerPortfolio saved = repository.save(WorkerPortfolio.builder()
                .userId(userId)
                .title(request.title().trim())
                .description(blankToNull(request.description()))
                .category(blankToNull(request.category()))
                .completionDate(request.completionDate())
                .imageUrl(photo.url())
                .mediaId(photo.id())
                .build());
        log.info("Portfolio item {} added by user {}", saved.getId(), userId);
        return saved;
    }

    /** Owner only. Someone else's item answers as not found. */
    @Transactional
    public void delete(Long userId, Long itemId) {
        WorkerPortfolio item = repository.findById(itemId)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Portfolio item not found"));
        repository.delete(item);
        log.info("Portfolio item {} removed by user {}", itemId, userId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
