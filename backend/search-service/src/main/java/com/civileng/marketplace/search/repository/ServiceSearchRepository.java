package com.civileng.marketplace.search.repository;

import com.civileng.marketplace.search.document.ServiceDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceSearchRepository
        extends ElasticsearchRepository<ServiceDocument, String> {

    Page<ServiceDocument> findByActiveTrue(Pageable pageable);
}
