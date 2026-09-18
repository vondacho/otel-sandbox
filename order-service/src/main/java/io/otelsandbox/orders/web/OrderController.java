package io.otelsandbox.orders.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.otelsandbox.orders.order.Order;
import io.otelsandbox.orders.order.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<Order> place(@Valid @RequestBody PlaceOrderRequest request) {
        Order order = orderService.placeOrder(request.sku(), request.quantity());
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId())).body(order);
    }

    @GetMapping
    List<Order> latest() {
        return orderService.latestOrders();
    }

    @GetMapping("/{id}")
    ResponseEntity<Order> get(@PathVariable UUID id) {
        return ResponseEntity.of(orderService.find(id));
    }
}
