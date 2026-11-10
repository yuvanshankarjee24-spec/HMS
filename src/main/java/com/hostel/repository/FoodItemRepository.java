package com.hostel.repository;

import com.hostel.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {
    boolean existsByNameIgnoreCase(String name);
    List<FoodItem> findByActiveTrueOrderByNameAsc();
    List<FoodItem> findAllByOrderByNameAsc();
}

