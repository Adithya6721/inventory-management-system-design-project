package com.example.inventory.notification;

import com.example.inventory.integration.IdGeneratorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LowStockServiceTest {

    @Mock
    private LowStockAlertRepository lowStockAlertRepository;

    @Mock
    private IdGeneratorPort idGeneratorPort;

    private LowStockService lowStockService;

    @BeforeEach
    void setUp() {
        lowStockService = new LowStockService(lowStockAlertRepository, idGeneratorPort);
    }

    @Test
    @DisplayName("Low Stock Alert: Creates new alert when available stock <= reorder level")
    void testEvaluateStock_TriggersAlert() {
        when(lowStockAlertRepository.findByProductIdAndWarehouseIdAndStatus("prod_1", "wh_1", AlertStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(idGeneratorPort.generateId("alert")).thenReturn("alert_101");

        lowStockService.evaluateStock("prod_1", "wh_1", 5, 10);

        ArgumentCaptor<LowStockAlert> captor = ArgumentCaptor.forClass(LowStockAlert.class);
        verify(lowStockAlertRepository).save(captor.capture());

        LowStockAlert saved = captor.getValue();
        assertEquals("alert_101", saved.getAlertId());
        assertEquals("prod_1", saved.getProductId());
        assertEquals(5, saved.getAvailableQuantity());
        assertEquals(AlertStatus.ACTIVE, saved.getStatus());
    }

    @Test
    @DisplayName("Low Stock Alert Deduplication: Updates existing active alert without creating a duplicate record")
    void testEvaluateStock_DeduplicatesExistingActiveAlert() {
        LowStockAlert existing = new LowStockAlert("alert_101", "prod_1", "wh_1", 10, 5, AlertStatus.ACTIVE);
        when(lowStockAlertRepository.findByProductIdAndWarehouseIdAndStatus("prod_1", "wh_1", AlertStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        // Further stock drop to 3
        lowStockService.evaluateStock("prod_1", "wh_1", 3, 10);

        verify(idGeneratorPort, never()).generateId(any());
        verify(lowStockAlertRepository).save(existing);
        assertEquals(3, existing.getAvailableQuantity());
    }

    @Test
    @DisplayName("Low Stock Alert Resolution: Automatically marks alert RESOLVED when stock rises above threshold")
    void testEvaluateStock_ResolvesAlert() {
        LowStockAlert existing = new LowStockAlert("alert_101", "prod_1", "wh_1", 10, 5, AlertStatus.ACTIVE);
        when(lowStockAlertRepository.findByProductIdAndWarehouseIdAndStatus("prod_1", "wh_1", AlertStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        // Stock restored to 25 (threshold 10)
        lowStockService.evaluateStock("prod_1", "wh_1", 25, 10);

        verify(lowStockAlertRepository).save(existing);
        assertEquals(AlertStatus.RESOLVED, existing.getStatus());
        assertEquals(25, existing.getAvailableQuantity());
    }
}
