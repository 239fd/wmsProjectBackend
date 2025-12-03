package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.Pallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PalletRepository extends JpaRepository<Pallet, UUID> {
}
