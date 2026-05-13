package com.darkstore.repository;

import com.darkstore.model.InventorySnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, Long> {

    /** Latest snapshot for a product at a store */
    Optional<InventorySnapshot> findTopByStore_IdAndProduct_IdOrderBySnapshotTimeDesc(
            String storeId, String productId);

    /** All snapshots for a store within a time window */
    List<InventorySnapshot> findByStore_IdAndSnapshotTimeBetweenOrderBySnapshotTimeAsc(
            String storeId, LocalDateTime from, LocalDateTime to);

    /** All snapshots for a product at a store (for history chart) */
    List<InventorySnapshot> findByStore_IdAndProduct_IdAndSnapshotTimeBetweenOrderBySnapshotTimeAsc(
            String storeId, String productId, LocalDateTime from, LocalDateTime to);

    /**
     * Latest stock level per product for a given store.
     * Used by the Reorder Engine for a full stock sweep.
     */
    @Query("""
            SELECT s FROM InventorySnapshot s
            WHERE s.store.id = :storeId
              AND s.snapshotTime = (
                SELECT MAX(s2.snapshotTime)
                FROM InventorySnapshot s2
                WHERE s2.store.id = :storeId
                  AND s2.product.id = s.product.id
              )
            """)
    List<InventorySnapshot> findLatestSnapshotPerProductForStore(@Param("storeId") String storeId);

    /** Products with inventory below a given threshold at a store */
    @Query("""
            SELECT s FROM InventorySnapshot s
            WHERE s.store.id = :storeId
              AND s.inventoryLevel <= :threshold
              AND s.snapshotTime = (
                SELECT MAX(s2.snapshotTime)
                FROM InventorySnapshot s2
                WHERE s2.store.id = :storeId
                  AND s2.product.id = s.product.id
              )
            """)
    List<InventorySnapshot> findLowStockByStore(@Param("storeId") String storeId,
                                                @Param("threshold") int threshold);

    /** Recent sales for a product across all stores (last N snapshots) */
    List<InventorySnapshot> findByProduct_IdOrderBySnapshotTimeDesc(String productId, Pageable pageable);
}
