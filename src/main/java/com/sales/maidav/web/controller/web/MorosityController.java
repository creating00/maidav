package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.sale.MorosityLevel;
import com.sales.maidav.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/arrears")
@RequiredArgsConstructor
public class MorosityController {

    private final CreditAccountService creditAccountService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String list(@RequestParam(required = false) String level,
                       @RequestParam(required = false) Long sellerId,
                       Authentication authentication,
                       Model model) {
        MorosityLevel filter = null;
        if (level != null && !level.isBlank()) {
            filter = MorosityLevel.valueOf(level.toUpperCase());
        }
        Long effectiveSellerId = resolveSellerFilter(authentication, sellerId);
        model.addAttribute("level", level == null ? "" : level);
        model.addAttribute("sellerId", effectiveSellerId);
        // FILTRO POR VENDEDOR
        // FILTRO VENDEDOR FRONTEND
        model.addAttribute("sellerOptions", sellerOptions(authentication));
        // FILTRO VENDEDOR BACKEND
        model.addAttribute("rows", creditAccountService.getMorosity(filter, effectiveSellerId));
        return "pages/arrears/index";
    }

    private Long resolveSellerFilter(Authentication authentication, Long sellerId) {
        if (isAdmin(authentication)) {
            return sellerId == null || sellerId <= 0 ? null : sellerId;
        }
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userService.findByEmail(authentication.getName()).getId();
    }

    private java.util.List<SellerOption> sellerOptions(Authentication authentication) {
        if (isAdmin(authentication)) {
            return userService.findByRoleName("VENDEDOR").stream()
                    .map(user -> new SellerOption(user.getId(), sellerLabel(user)))
                    .toList();
        }
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return java.util.List.of();
        }
        var currentUser = userService.findByEmail(authentication.getName());
        return java.util.List.of(new SellerOption(currentUser.getId(), sellerLabel(currentUser)));
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private String sellerLabel(com.sales.maidav.model.user.User user) {
        if (user == null) {
            return "-";
        }
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? user.getEmail() : fullName;
    }

    private record SellerOption(Long id, String label) {
    }
}
