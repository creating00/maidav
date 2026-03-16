package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.sale.CreditAccount;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MassPaymentImportService {

    private final CreditAccountService creditAccountService;

    public MassPaymentImportService(CreditAccountService creditAccountService) {
        this.creditAccountService = creditAccountService;
    }

    public MassPaymentImportSummary importWorkbook(MultipartFile file,
                                                   PaymentCollectionMethod defaultMethod,
                                                   String registeredBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Seleccione un archivo Excel para importar");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xls") && !fileName.endsWith(".xlsx")) {
            throw new IllegalArgumentException("El archivo debe ser .xls o .xlsx");
        }

        int processedRows = 0;
        int successCount = 0;
        BigDecimal cashTotal = BigDecimal.ZERO;
        BigDecimal bankTotal = BigDecimal.ZERO;
        List<String> errors = new ArrayList<>();
        List<MassPaymentImportSummary.ImportedPaymentView> importedPayments = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("El archivo no contiene hojas para procesar");
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            HeaderMapping headerMapping = detectHeaderMapping(sheet, formatter, evaluator);
            int startRow = headerMapping.headerRowIndex() >= 0 ? headerMapping.headerRowIndex() + 1 : sheet.getFirstRowNum();

            for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, formatter, evaluator)) {
                    continue;
                }
                processedRows++;
                try {
                    PaymentEntry entry = parseRow(row, rowIndex, headerMapping, formatter, evaluator, defaultMethod);
                    CreditAccount account = creditAccountService.findById(entry.accountId());
                    creditAccountService.registerPayment(
                            entry.accountId(),
                            entry.amount(),
                            null,
                            registeredBy,
                            entry.paymentMethod()
                    );
                    importedPayments.add(new MassPaymentImportSummary.ImportedPaymentView(
                            account.getId(),
                            account.getAccountNumber(),
                            buildClientName(account),
                            entry.amount(),
                            entry.paymentMethod()
                    ));
                    successCount++;
                    if (entry.paymentMethod() == PaymentCollectionMethod.CASH) {
                        cashTotal = cashTotal.add(entry.amount());
                    } else {
                        bankTotal = bankTotal.add(entry.amount());
                    }
                } catch (RuntimeException ex) {
                    errors.add("Fila " + (rowIndex + 1) + ": " + ex.getMessage());
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo procesar el archivo Excel");
        }

        if (processedRows == 0) {
            throw new IllegalArgumentException("No se encontraron filas validas para importar");
        }

        return new MassPaymentImportSummary(processedRows, successCount, errors, cashTotal, bankTotal, importedPayments);
    }

    private HeaderMapping detectHeaderMapping(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int lastRow = Math.min(sheet.getLastRowNum(), 9);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int accountColumn = -1;
            int amountColumn = -1;
            int methodColumn = -1;
            for (int cellIndex = row.getFirstCellNum(); cellIndex < row.getLastCellNum(); cellIndex++) {
                if (cellIndex < 0) {
                    continue;
                }
                String header = normalize(formatCellValue(row.getCell(cellIndex), formatter, evaluator));
                if (header.isEmpty()) {
                    continue;
                }
                if (accountColumn < 0 && isAccountHeader(header)) {
                    accountColumn = cellIndex;
                    continue;
                }
                if (amountColumn < 0 && isAmountHeader(header)) {
                    amountColumn = cellIndex;
                    continue;
                }
                if (methodColumn < 0 && isMethodHeader(header)) {
                    methodColumn = cellIndex;
                }
            }
            if (accountColumn >= 0 && amountColumn >= 0) {
                return new HeaderMapping(rowIndex, accountColumn, amountColumn, methodColumn);
            }
        }
        return new HeaderMapping(-1, -1, -1, -1);
    }

    private PaymentEntry parseRow(Row row,
                                  int rowIndex,
                                  HeaderMapping headerMapping,
                                  DataFormatter formatter,
                                  FormulaEvaluator evaluator,
                                  PaymentCollectionMethod defaultMethod) {
        if (headerMapping.hasHeaders()) {
            String accountRaw = formatCellValue(row.getCell(headerMapping.accountColumn()), formatter, evaluator);
            String amountRaw = formatCellValue(row.getCell(headerMapping.amountColumn()), formatter, evaluator);
            String methodRaw = headerMapping.methodColumn() >= 0
                    ? formatCellValue(row.getCell(headerMapping.methodColumn()), formatter, evaluator)
                    : "";
            return new PaymentEntry(
                    parseAccountId(accountRaw),
                    parseAmount(amountRaw),
                    resolveMethod(methodRaw, defaultMethod)
            );
        }

        List<String> values = new ArrayList<>();
        short lastCellNum = row.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            String value = formatCellValue(row.getCell(cellIndex), formatter, evaluator).trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Fila vacia");
        }

        int accountIndex = -1;
        int amountIndex = -1;
        for (int i = 0; i < values.size(); i++) {
            if (accountIndex < 0 && isInteger(values.get(i))) {
                accountIndex = i;
                continue;
            }
            if (accountIndex >= 0 && isDecimal(values.get(i))) {
                amountIndex = i;
                break;
            }
        }
        if (accountIndex < 0 || amountIndex < 0) {
            throw new IllegalArgumentException("No se pudo identificar ID credito y monto");
        }

        String methodRaw = "";
        if (amountIndex + 1 < values.size() && !isDecimal(values.get(amountIndex + 1)) && !isInteger(values.get(amountIndex + 1))) {
            methodRaw = values.get(amountIndex + 1);
        }

        return new PaymentEntry(
                parseAccountId(values.get(accountIndex)),
                parseAmount(values.get(amountIndex)),
                resolveMethod(methodRaw, defaultMethod)
        );
    }

    private boolean isBlankRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (int cellIndex = row.getFirstCellNum(); cellIndex < row.getLastCellNum(); cellIndex++) {
            if (cellIndex < 0) {
                continue;
            }
            if (!formatCellValue(row.getCell(cellIndex), formatter, evaluator).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String formatCellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator);
    }

    private Long parseAccountId(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (!normalized.matches("^\\d+$")) {
            throw new IllegalArgumentException("El ID credito debe ser numerico");
        }
        return Long.parseLong(normalized);
    }

    private BigDecimal parseAmount(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }
        try {
            BigDecimal amount = new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El monto debe ser mayor a cero");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El monto no es valido");
        }
    }

    private PaymentCollectionMethod resolveMethod(String rawValue, PaymentCollectionMethod defaultMethod) {
        String normalized = normalize(rawValue);
        if (normalized.isEmpty()) {
            return defaultMethod == null ? PaymentCollectionMethod.BANK : defaultMethod;
        }
        if (normalized.contains("efectivo") || normalized.contains("cash")) {
            return PaymentCollectionMethod.CASH;
        }
        if (normalized.contains("debito") || normalized.contains("transfer") || normalized.contains("transf")
                || normalized.contains("bank") || normalized.contains("banco")) {
            return PaymentCollectionMethod.BANK;
        }
        throw new IllegalArgumentException("Metodo de pago no reconocido: " + rawValue);
    }

    private boolean isAccountHeader(String header) {
        return header.contains("id credito")
                || header.contains("credito id")
                || header.equals("credito")
                || header.equals("id")
                || header.contains("id de credito");
    }

    private boolean isAmountHeader(String header) {
        return header.contains("monto")
                || header.contains("pago")
                || header.contains("cobro")
                || header.contains("importe");
    }

    private boolean isMethodHeader(String header) {
        return header.contains("metodo")
                || header.contains("medio")
                || header.contains("forma de pago");
    }

    private boolean isInteger(String value) {
        String normalized = value == null ? "" : value.trim();
        return !normalized.isEmpty() && normalized.matches("^\\d+$");
    }

    private boolean isDecimal(String value) {
        String normalized = value == null ? "" : value.trim().replace(" ", "");
        return !normalized.isEmpty() && normalized.matches("^\\d+(?:[\\.,]\\d+)?$");
    }

    private String buildClientName(CreditAccount account) {
        if (account == null || account.getClient() == null) {
            return "-";
        }
        String firstName = account.getClient().getFirstName() == null ? "" : account.getClient().getFirstName().trim();
        String lastName = account.getClient().getLastName() == null ? "" : account.getClient().getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? "-" : fullName;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private record HeaderMapping(int headerRowIndex, int accountColumn, int amountColumn, int methodColumn) {
        private boolean hasHeaders() {
            return headerRowIndex >= 0 && accountColumn >= 0 && amountColumn >= 0;
        }
    }

    private record PaymentEntry(Long accountId, BigDecimal amount, PaymentCollectionMethod paymentMethod) {}
}
