package com.hostel.repository;

import com.hostel.entity.RequestRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequestRatingRepository extends JpaRepository<RequestRating, Long> {
    Optional<RequestRating> findByRequestId(Long requestId);
    boolean existsByRequestId(Long requestId);

    @Query("SELECT AVG(r.rating) FROM RequestRating r WHERE r.worker.id = :workerId")
    Double findAverageRatingByWorkerId(@Param("workerId") Long workerId);
}

