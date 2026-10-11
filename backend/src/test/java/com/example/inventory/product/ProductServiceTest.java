package com.example.inventory.product;

import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.integration.InventoryCachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private IdGeneratorPort idGeneratorPort;

    @Mock
    private InventoryCachePort inventoryCachePort;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, idGeneratorPort, inventoryCachePort);
    }

    @Test
    @DisplayName("Register Product: Successfully registers and evicts cache")
    void testRegisterProduct_Success() {
        ProductRequest request = new ProductRequest("SKU-100", "Widget A", "Test widget", "Electronics", new BigDecimal("49.99"), 15, true);

        when(productRepository.existsBySku("SKU-100")).thenReturn(false);
        when(idGeneratorPort.generateId("prod")).thenReturn("prod_123");
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.registerProduct(request);

        assertNotNull(response);
        assertEquals("prod_123", response.getProductId());
        assertEquals("SKU-100", response.getSku());
        assertEquals("Widget A", response.getName());
        verify(inventoryCachePort).evictProduct("prod_123");
    }

    @Test
    @DisplayName("Register Product: Rejects duplicate SKU with ConflictException")
    void testRegisterProduct_DuplicateSku() {
        ProductRequest request = new ProductRequest("SKU-DUP", "Widget D", "Desc", "Category", new BigDecimal("10.0"), 5, true);
        when(productRepository.existsBySku("SKU-DUP")).thenReturn(true);

        assertThrows(ConflictException.class, () -> productService.registerProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get Product: Throws NotFoundException if not exists")
    void testGetProduct_NotFound() {
        when(productRepository.findByProductId("prod_999")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> productService.getProduct("prod_999"));
    }

    @Test
    @DisplayName("Search Products: Returns paginated response")
    void testSearchProducts() {
        Product product = new Product("prod_1", "SKU-1", "Laptop Pro", "Desc", "Computers", new BigDecimal("1200"), 5, true);
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(productRepository.findByCategoryIgnoreCaseAndNameContainingIgnoreCase("Computers", "Laptop", pageRequest))
                .thenReturn(new PageImpl<>(List.of(product), pageRequest, 1));

        Page<ProductResponse> page = productService.searchProducts("Laptop", "Computers", pageRequest);

        assertEquals(1, page.getTotalElements());
        assertEquals("Laptop Pro", page.getContent().get(0).getName());
    }
}
