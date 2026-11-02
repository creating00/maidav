package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.product.InvalidProductException;
import com.sales.maidav.service.product.ProductImageExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ProductImageExportControllerTest.Config.class)
class ProductImageExportControllerTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProductImageExportService service() { return mock(ProductImageExportService.class); }
        @Bean ProductImageExportController controller(ProductImageExportService service) {
            return new ProductImageExportController(service);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ProductImageExportService service;
    @org.springframework.beans.factory.annotation.Autowired
    private ProductImageExportController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_READ")
    void downloadsZipWithAttachmentHeaders() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(0, OutputStream.class).write(new byte[]{80, 75, 3, 4});
            return null;
        }).when(service).writeZip(any());
        mvc.perform(get("/products/images/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.matchesPattern(
                        "attachment; filename=imagenes-productos-\\d{4}-\\d{2}-\\d{2}\\.zip")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(new byte[]{80, 75, 3, 4}));
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_READ")
    void redirectsWithMessageWhenNoImagesExist() throws Exception {
        doThrow(new InvalidProductException("No hay imágenes de productos para exportar."))
                .when(service).writeZip(any());
        mvc.perform(get("/products/images/export"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("errorMessage", "No hay imágenes de productos para exportar."));
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_READ")
    void deletesTemporaryArchiveAndDoesNotSendPartialDownloadOnFailure() throws Exception {
        AtomicReference<Path> temporaryFile = new AtomicReference<>();
        doAnswer(invocation -> {
            try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
                temporaryFile.set(files.filter(path -> path.getFileName().toString().startsWith("product-images-"))
                        .findFirst().orElseThrow());
            }
            throw new IOException("Simulated disk failure");
        }).when(service).writeZip(any());
        mvc.perform(get("/products/images/export"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/products"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertThat(temporaryFile.get()).doesNotExist();
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_UPDATE")
    void deniesExportWithoutProductReadPermission() {
        assertThatThrownBy(() -> mvc.perform(get("/products/images/export")))
                .hasRootCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(service);
    }
}
