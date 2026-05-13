package com.darkstore.repository;

import com.darkstore.model.DarkStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DarkStoreRepository extends JpaRepository<DarkStore, String> {
    List<DarkStore> findByRegion(String region);
    List<DarkStore> findByIsActiveTrue();
}
