package com.darkstore.repository;

import com.darkstore.model.ReorderEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReorderEventRepository extends JpaRepository<ReorderEvent, Long> {

    List<ReorderEvent> findByStatusOrderByTriggeredAtDesc(String status);

    List<ReorderEvent> findByStore_IdOrderByTriggeredAtDesc(String storeId);

    Page<ReorderEvent> findByStore_IdAndStatusOrderByTriggeredAtDesc(
            String storeId, String status, Pageable pageable);

    /** Check if a reorder already exists for store+product+reason within the past N hours */
    @Query("""
            SELECT COUNT(r) > 0 FROM ReorderEvent r
            WHERE r.store.id = :storeId
              AND r.product.id = :productId
              AND r.triggerReason = :reason
              AND r.status IN ('PENDING', 'APPROVED')
              AND r.triggeredAt >= :since
            """)
    boolean existsRecentReorder(@Param("storeId") String storeId,
                                @Param("productId") String productId,
                                @Param("reason") String reason,
                                @Param("since") LocalDateTime since);

    /** Dashboard KPI: total reorders triggered today */
    @Query("SELECT COUNT(r) FROM ReorderEvent r WHERE r.triggeredAt >= :startOfDay")
    long countReordersToday(@Param("startOfDay") LocalDateTime startOfDay);

    /** Dashboard KPI: pending reorders count */
    long countByStatus(String status);
}
