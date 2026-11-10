package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "menu_schedule")
public class MenuSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessPeriodConfig.Period period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItem foodItem;

    @Column(name = "daily_stock", nullable = false)
    private int dailyStock;

    @Column(name = "remaining_stock", nullable = false)
    private int remainingStock;

    @Column(name = "max_per_student", nullable = false)
    private int maxPerStudent = 2;

    @Column(name = "is_special_menu", nullable = false)
    private boolean specialMenu = false;

    @Column(name = "mess_closed", nullable = false)
    private boolean messClosed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public MenuSchedule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getMenuDate() { return menuDate; }
    public void setMenuDate(LocalDate menuDate) { this.menuDate = menuDate; }
    public MessPeriodConfig.Period getPeriod() { return period; }
    public void setPeriod(MessPeriodConfig.Period period) { this.period = period; }
    public FoodItem getFoodItem() { return foodItem; }
    public void setFoodItem(FoodItem foodItem) { this.foodItem = foodItem; }
    public int getDailyStock() { return dailyStock; }
    public void setDailyStock(int dailyStock) { this.dailyStock = dailyStock; }
    public int getRemainingStock() { return remainingStock; }
    public void setRemainingStock(int remainingStock) { this.remainingStock = remainingStock; }
    public int getMaxPerStudent() { return maxPerStudent; }
    public void setMaxPerStudent(int maxPerStudent) { this.maxPerStudent = maxPerStudent; }
    public boolean isSpecialMenu() { return specialMenu; }
    public void setSpecialMenu(boolean specialMenu) { this.specialMenu = specialMenu; }
    public boolean isMessClosed() { return messClosed; }
    public void setMessClosed(boolean messClosed) { this.messClosed = messClosed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

