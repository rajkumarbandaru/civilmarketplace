package com.civileng.marketplace.search.repository;

import com.civileng.marketplace.search.document.ProfileDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileSearchRepository
        extends ElasticsearchRepository<ProfileDocument, String> {
}
