package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.StockDeposito;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface StockDepositoRepository extends BaseRepository<StockDeposito> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockDeposito> findByDeposito_IdAndProducto_Id(Long depositoId, Long productoId);
}
