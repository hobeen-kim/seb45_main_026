package com.server.domain.order.service.dto.response;

import com.server.domain.order.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder
@Getter
public class OrderResponse {

    private String orderId;
    private Integer totalAmount;

    public static OrderResponse of(Order order){
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .totalAmount(order.getTotalPayAmount())
                .build();
    }
}
