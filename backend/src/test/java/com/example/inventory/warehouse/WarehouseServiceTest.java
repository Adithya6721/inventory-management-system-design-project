package com.example.inventory.warehouse;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private IdGeneratorPort idGeneratorPort;

    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        warehouseService = new WarehouseService(warehouseRepository, idGeneratorPort);
    }

    @Test
    @DisplayName("Create Warehouse: Successfully creates active warehouse")
    void testCreateWarehouse() {
        WarehouseRequest request = new WarehouseRequest("North Hub", "New York", WarehouseStatus.ACTIVE);
        when(idGeneratorPort.generateId("wh")).thenReturn("wh_north");
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.createWarehouse(request);

        assertNotNull(response);
        assertEquals("wh_north", response.getWarehouseId());
        assertEquals("North Hub", response.getName());
        assertEquals(WarehouseStatus.ACTIVE, response.getStatus());
    }

    @Test
    @DisplayName("Validate Warehouse Active: Throws BadRequestException if inactive")
    void testValidateWarehouseActive_Inactive() {
        Warehouse inactive = new Warehouse("wh_inactive", "Old Hub", "Dallas", WarehouseStatus.INACTIVE);
        when(warehouseRepository.findByWarehouseId("wh_inactive")).thenReturn(Optional.of(inactive));

        assertThrows(BadRequestException.class, () -> warehouseService.validateWarehouseActive("wh_inactive"));
    }

    @Test
    @DisplayName("Get Warehouse: Throws NotFoundException if missing")
    void testGetWarehouse_NotFound() {
        when(warehouseRepository.findByWarehouseId("wh_missing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> warehouseService.getWarehouse("wh_missing"));
    }
}
