package com.sales.maidav.service.expense;

import com.sales.maidav.model.expense.*;
import com.sales.maidav.repository.expense.*;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.web.dto.ExpenseForm;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseServiceImpl implements ExpenseService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final int MAX_RECEIPT_SIZE = 5 * 1024 * 1024;
    private final ExpenseRepository expenseRepository;
    private final ExpenseReceiptRepository receiptRepository;
    private final ExpenseIdempotencyKeyRepository keyRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Expense create(ExpenseForm form, Long userId, MultipartFile receipt) {
        String key;
        try {
            if (userId == null || form.getOperationKey() == null || form.getOperationKey().length() != 36) throw new IllegalArgumentException();
            key = UUID.fromString(form.getOperationKey()).toString();
        } catch (IllegalArgumentException ex) {
            throw new InvalidExpenseException("No se pudo validar el intento de gasto. Abra un formulario nuevo");
        }
        keyRepository.insertIfAbsent(userId, key);
        ExpenseIdempotencyKey request = keyRepository.findForUpdate(userId, key)
                .orElseThrow(() -> new IllegalStateException("No se pudo registrar el intento de gasto"));
        if (request.getExpense() != null) return request.getExpense();
        validate(form);

        Expense expense = new Expense();
        expense.setExpenseDate(form.getExpenseDate());
        expense.setCategory(form.getCategory());
        expense.setDescription(form.getDescription().trim());
        expense.setAmount(form.getAmount().setScale(2));
        expense.setPaymentMethod(form.getPaymentMethod());
        expense.setRegisteredBy(userRepository.findById(userId)
                .orElseThrow(() -> new InvalidExpenseException("Usuario no encontrado")));
        byte[] bytes = null;
        if (receipt != null && !receipt.isEmpty()) {
            if (receipt.getSize() > MAX_RECEIPT_SIZE) throw new InvalidExpenseException("El comprobante debe pesar hasta 5 MB");
            try (var input = receipt.getInputStream()) {
                bytes = input.readNBytes(MAX_RECEIPT_SIZE + 1);
            } catch (IOException ex) {
                throw new InvalidExpenseException("No se pudo leer el comprobante. Selecciónelo nuevamente");
            }
            if (bytes.length > MAX_RECEIPT_SIZE) throw new InvalidExpenseException("El comprobante debe pesar hasta 5 MB");
            expense.setReceiptContentType(detectContentType(bytes));
            String name = Objects.toString(receipt.getOriginalFilename(), "comprobante").replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
            if (name.isBlank()) name = "comprobante";
            String extension = switch (expense.getReceiptContentType()) {
                case "application/pdf" -> ".pdf";
                case "image/png" -> ".png";
                default -> ".jpg";
            };
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            expense.setReceiptName(base.substring(0, Math.min(base.length(), 200 - extension.length())) + extension);
            expense.setReceiptSize(bytes.length);
        }
        expenseRepository.saveAndFlush(expense);
        if (bytes != null) receiptRepository.save(new ExpenseReceipt(expense.getId(), bytes));
        request.setExpense(expense);
        return expense;
    }

    @Override
    public Expense findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));
    }

    @Override
    public ExpenseReceipt findReceipt(Long id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));
    }

    @Override
    public Page<Expense> findPage(LocalDate from, LocalDate to, ExpenseCategory category, BigDecimal minAmount, BigDecimal maxAmount, int page) {
        if (from != null && to != null && from.isAfter(to)) throw new InvalidExpenseException("La fecha desde no puede ser posterior a la fecha hasta");
        if (minAmount != null) validateAmount(minAmount, true);
        if (maxAmount != null) validateAmount(maxAmount, true);
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new InvalidExpenseException("El importe mínimo no puede superar al máximo");
        }
        return expenseRepository.findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (from != null) filters.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            if (to != null) filters.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            if (category != null) filters.add(cb.equal(root.get("category"), category));
            if (minAmount != null) filters.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            if (maxAmount != null) filters.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.DESC, "expenseDate", "id")));
    }

    private void validate(ExpenseForm form) {
        if (form.getExpenseDate() == null || form.getExpenseDate().isAfter(LocalDate.now())) throw new InvalidExpenseException("Ingrese una fecha de gasto válida, hasta hoy");
        if (form.getCategory() == null) throw new InvalidExpenseException("Seleccione la categoría del gasto");
        if (form.getDescription() == null || form.getDescription().isBlank() || form.getDescription().length() > 1000) {
            throw new InvalidExpenseException("Ingrese una descripción de hasta 1000 caracteres");
        }
        if (form.getPaymentMethod() == null) throw new InvalidExpenseException("Seleccione la forma de pago");
        validateAmount(form.getAmount(), false);
    }

    private void validateAmount(BigDecimal amount, boolean allowZero) {
        if (amount == null || amount.signum() < (allowZero ? 0 : 1) || amount.compareTo(MAX_AMOUNT) > 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidExpenseException("El importe debe ser " + (allowZero ? "cero o positivo" : "positivo") + ", con hasta 2 decimales y hasta $" + MAX_AMOUNT);
        }
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') return "application/pdf";
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) return "image/jpeg";
        throw new InvalidExpenseException("El comprobante debe ser un archivo PDF, JPG o PNG");
    }
}
