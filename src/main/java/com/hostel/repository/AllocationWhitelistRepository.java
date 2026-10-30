package com.hostel.repository;

import com.hostel.entity.AllocationWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationWhitelistRepository extends JpaRepository<AllocationWhitelist, Long> {
    Optional<AllocationWhitelist> findByLeaderRegisterNumber(String leaderRegisterNumber);
    boolean existsByLeaderRegisterNumber(String leaderRegisterNumber);
    List<AllocationWhitelist> findAllByOrderByCreatedAtDesc();
    List<AllocationWhitelist> findByFriendRegisterNumbersContaining(String registerNumber);
}
