package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateSupplierRequest;
import by.bsuir.productservice.dto.response.SupplierResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Supplier;
import by.bsuir.productservice.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public List<SupplierResponse> getAll(UUID organizationId) {
        List<Supplier> suppliers = (organizationId != null)
                ? supplierRepository.findByOrganizationIdAndIsActiveTrue(organizationId)
                : supplierRepository.findByIsActiveTrue();
        return suppliers.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> getAll(UUID organizationId, Pageable pageable) {
        Page<Supplier> suppliers = (organizationId != null)
                ? supplierRepository.findByOrganizationIdAndIsActiveTrue(organizationId, pageable)
                : supplierRepository.findByIsActiveTrue(pageable);
        return suppliers.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getById(UUID supplierId, UUID organizationId) {
        return toResponse(findActive(supplierId, organizationId));
    }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest request, UUID organizationId) {
        log.info("Creating supplier: {} (org: {})", request.name(), organizationId);

        if (request.unp() != null && !request.unp().isBlank()) {
            boolean conflict = (organizationId != null)
                    ? supplierRepository.existsByOrganizationIdAndUnpAndIsActiveTrue(organizationId, request.unp())
                    : supplierRepository.existsByUnpAndIsActiveTrue(request.unp());
            if (conflict) {
                throw AppException.conflict("Поставщик с таким УНП уже существует");
            }
        }

        Supplier supplier = Supplier.builder()
                .organizationId(organizationId)
                .name(request.name())
                .unp(request.unp())
                .contactPerson(request.contactPerson())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .isActive(true)
                .build();

        supplierRepository.save(supplier);
        log.info("Supplier created: {}", supplier.getSupplierId());
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse update(UUID supplierId, CreateSupplierRequest request, UUID organizationId) {
        log.info("Updating supplier: {}", supplierId);
        Supplier supplier = findActive(supplierId, organizationId);

        if (request.unp() != null && !request.unp().isBlank()
                && !request.unp().equals(supplier.getUnp())) {
            boolean conflict = (organizationId != null)
                    ? supplierRepository.existsByOrganizationIdAndUnpAndIsActiveTrue(organizationId, request.unp())
                    : supplierRepository.existsByUnpAndIsActiveTrue(request.unp());
            if (conflict) {
                throw AppException.conflict("Поставщик с таким УНП уже существует");
            }
        }

        if (request.name() != null) supplier.setName(request.name());
        if (request.unp() != null) supplier.setUnp(request.unp());
        if (request.contactPerson() != null) supplier.setContactPerson(request.contactPerson());
        if (request.phone() != null) supplier.setPhone(request.phone());
        if (request.email() != null) supplier.setEmail(request.email());
        if (request.address() != null) supplier.setAddress(request.address());

        supplierRepository.save(supplier);
        return toResponse(supplier);
    }

    @Transactional
    public void delete(UUID supplierId, UUID organizationId) {
        log.info("Deactivating supplier: {}", supplierId);
        Supplier supplier = findActive(supplierId, organizationId);
        supplier.setIsActive(false);
        supplierRepository.save(supplier);
    }

    private Supplier findActive(UUID supplierId, UUID organizationId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .orElseThrow(() -> AppException.notFound("Поставщик не найден"));
        if (organizationId != null && supplier.getOrganizationId() != null
                && !organizationId.equals(supplier.getOrganizationId())) {
            throw AppException.forbidden("Поставщик принадлежит другой организации");
        }
        return supplier;
    }

    private SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(
                s.getSupplierId(),
                s.getOrganizationId(),
                s.getName(),
                s.getUnp(),
                s.getContactPerson(),
                s.getPhone(),
                s.getEmail(),
                s.getAddress(),
                s.getIsActive(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}