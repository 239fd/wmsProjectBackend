package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.SagaState;
import by.bsuir.productservice.model.enums.SagaStatus;
import by.bsuir.productservice.model.enums.SagaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {

    List<SagaState> findByStatus(SagaStatus status);

    List<SagaState> findBySagaTypeAndStatus(SagaType sagaType, SagaStatus status);

    List<SagaState> findByStatusIn(List<SagaStatus> statuses);
}