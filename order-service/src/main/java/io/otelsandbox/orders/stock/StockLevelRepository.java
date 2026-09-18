package io.otelsandbox.orders.stock;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StockLevelRepository extends JpaRepository<StockLevel, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.sku = :sku")
    Optional<StockLevel> findForUpdate(String sku);

    List<StockLevel> findAllByOrderBySkuAsc();
}
