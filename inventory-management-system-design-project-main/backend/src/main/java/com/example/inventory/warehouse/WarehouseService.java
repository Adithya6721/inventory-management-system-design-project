package com.example.inventory.warehouse;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final IdGeneratorPort idGeneratorPort;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            IdGeneratorPort idGeneratorPort) {
        this.warehouseRepository = warehouseRepository;
        this.idGeneratorPort = idGeneratorPort;
    }

    public WarehouseResponse createWarehouse(WarehouseRequest request) {
        String warehouseId = idGeneratorPort.generateId("wh");
        Warehouse warehouse = new Warehouse(
                warehouseId,
                request.getName().trim(),
                request.getLocation().trim(),
                request.getStatus() != null ? request.getStatus() : WarehouseStatus.ACTIVE
        );

        Warehouse saved = warehouseRepository.save(warehouse);
        return WarehouseResponse.fromDomain(saved);
    }

    public WarehouseResponse getWarehouse(String warehouseId) {
        return WarehouseResponse.fromDomain(getWarehouseEntity(warehouseId));
    }

    public Warehouse getWarehouseEntity(String warehouseId) {
        return warehouseRepository.findByWarehouseId(warehouseId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found with ID: " + warehouseId));
    }

    public List<WarehouseResponse> listWarehouses() {
        return warehouseRepository.findAll().stream()
                .map(WarehouseResponse::fromDomain)
                .collect(Collectors.toList());
    }

    public WarehouseResponse updateWarehouse(String warehouseId, WarehouseRequest request) {
        Warehouse warehouse = getWarehouseEntity(warehouseId);

        warehouse.setName(request.getName().trim());
        warehouse.setLocation(request.getLocation().trim());
        if (request.getStatus() != null) {
            warehouse.setStatus(request.getStatus());
        }
        warehouse.setUpdatedAt(Instant.now());

        Warehouse saved = warehouseRepository.save(warehouse);
        return WarehouseResponse.fromDomain(saved);
    }

    public void validateWarehouseActive(String warehouseId) {
        Warehouse warehouse = getWarehouseEntity(warehouseId);
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BadRequestException("Warehouse '" + warehouseId + "' is not ACTIVE.");
        }
    }
}
