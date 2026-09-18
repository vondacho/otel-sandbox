package io.otelsandbox.orders.stock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StockServiceTest {

    @ParameterizedTest
    @CsvSource(nullValues = "none", value = {
            "10, 1,  none",
            "10, 10, none",
            "3,  5,  insufficient_stock",
            "0,  1,  out_of_stock",
            "-2, 1,  out_of_stock"
    })
    void decidesWhetherStockCanBeReserved(long available, int quantity, String expectedRejection) {
        assertThat(StockService.decide(available, quantity).orElse(null)).isEqualTo(expectedRejection);
    }
}
