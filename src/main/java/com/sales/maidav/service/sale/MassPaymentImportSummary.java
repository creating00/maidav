package com.sales.maidav.service.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.sales.maidav.model.sale.PaymentCollectionMethod;

public class MassPaymentImportSummary {

    private final int processedRows;
    private final int successCount;
    private final List<String> errors;
    private final BigDecimal cashTotal;
    private final BigDecimal bankTotal;
    private final List<ImportedPaymentView> importedPayments;

    public MassPaymentImportSummary(int processedRows,
                                    int successCount,
                                    List<String> errors,
                                    BigDecimal cashTotal,
                                    BigDecimal bankTotal,
                                    List<ImportedPaymentView> importedPayments) {
        this.processedRows = processedRows;
        this.successCount = successCount;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.cashTotal = normalize(cashTotal);
        this.bankTotal = normalize(bankTotal);
        this.importedPayments = importedPayments == null ? List.of() : List.copyOf(importedPayments);
    }

    public int getProcessedRows() {
        return processedRows;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getErrorCount() {
        return errors.size();
    }

    public List<String> getErrors() {
        return errors;
    }

    public BigDecimal getCashTotal() {
        return cashTotal;
    }

    public BigDecimal getBankTotal() {
        return bankTotal;
    }

    public List<ImportedPaymentView> getImportedPayments() {
        return importedPayments;
    }

    public BigDecimal getGeneralTotal() {
        return cashTotal.add(bankTotal).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isEmpty() {
        return processedRows == 0;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static class ImportedPaymentView {

        private final Long accountId;
        private final String accountNumber;
        private final String clientName;
        private final BigDecimal amount;
        private final PaymentCollectionMethod paymentMethod;

        public ImportedPaymentView(Long accountId,
                                   String accountNumber,
                                   String clientName,
                                   BigDecimal amount,
                                   PaymentCollectionMethod paymentMethod) {
            this.accountId = accountId;
            this.accountNumber = accountNumber;
            this.clientName = clientName;
            this.amount = amount == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : amount.setScale(2, RoundingMode.HALF_UP);
            this.paymentMethod = paymentMethod == null ? PaymentCollectionMethod.BANK : paymentMethod;
        }

        public Long getAccountId() {
            return accountId;
        }

        public String getAccountNumber() {
            return accountNumber;
        }

        public String getClientName() {
            return clientName;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public PaymentCollectionMethod getPaymentMethod() {
            return paymentMethod;
        }

        public String getPaymentMethodLabel() {
            return paymentMethod == PaymentCollectionMethod.CASH ? "Efectivo" : "Debito / Transferencia";
        }
    }
}
