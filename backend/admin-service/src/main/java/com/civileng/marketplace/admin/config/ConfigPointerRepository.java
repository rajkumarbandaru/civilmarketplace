package com.civileng.marketplace.admin.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigPointerRepository extends JpaRepository<ConfigPointer, ConfigPointer.Key> {

    List<ConfigPointer> findByIdScope(String scope);
}
