package io.otelsandbox.orders.web;

import java.util.List;

import io.otelsandbox.orders.stock.StockLevel;
import io.otelsandbox.orders.stock.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StockController {

    private final StockService stockService;

    StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/api/stock")
    List<StockLevel> stock() {
        return stockService.all();
    }
}
