package com.rinda.backend.plot;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlotRepository extends JpaRepository<Plot, Long> {

    Page<Plot> findByStatusNot(PlotStatus status, Pageable pageable);

    Page<Plot> findByFarmerIdAndStatusNot(long farmerId, PlotStatus status, Pageable pageable);

    Optional<Plot> findByIdAndStatusNot(long id, PlotStatus status);

    @Query("""
            select p from Plot p
            where p.status <> com.rinda.backend.plot.PlotStatus.ARCHIVED
              and p.farmerId in (select f.userId from Farmer f where f.cooperativeId = :cooperativeId)
            """)
    Page<Plot> findInCooperative(long cooperativeId, Pageable pageable);
}
