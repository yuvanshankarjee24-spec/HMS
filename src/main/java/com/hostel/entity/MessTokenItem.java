package com.hostel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "mess_token_items")
public class MessTokenItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id", nullable = false)
    private MessToken token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_schedule_id", nullable = false)
    private MenuSchedule menuSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItem foodItem;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "delivered_quantity")
    private Integer deliveredQuantity;

    @Column(name = "undelivered_reason", length = 500)
    private String undeliveredReason;

    public MessTokenItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MessToken getToken() { return token; }
    public void setToken(MessToken token) { this.token = token; }
    public MenuSchedule getMenuSchedule() { return menuSchedule; }
    public void setMenuSchedule(MenuSchedule menuSchedule) { this.menuSchedule = menuSchedule; }
    public FoodItem getFoodItem() { return foodItem; }
    public void setFoodItem(FoodItem foodItem) { this.foodItem = foodItem; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Integer getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(Integer deliveredQuantity) { this.deliveredQuantity = deliveredQuantity; }
    public String getUndeliveredReason() { return undeliveredReason; }
    public void setUndeliveredReason(String undeliveredReason) { this.undeliveredReason = undeliveredReason; }
}

