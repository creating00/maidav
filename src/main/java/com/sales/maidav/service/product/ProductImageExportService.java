package com.sales.maidav.service.product;

import java.io.IOException;
import java.io.OutputStream;

public interface ProductImageExportService {
    void writeZip(OutputStream output) throws IOException;
}
