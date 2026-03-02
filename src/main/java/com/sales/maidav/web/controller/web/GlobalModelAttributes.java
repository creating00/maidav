package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.user.UserService;
import com.sales.maidav.web.dto.CurrentUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.security.core.Authentication;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final ProductService productService;
    private final UserService userService;

    @ModelAttribute("lowStockCount")
    public long lowStockCount() {
        return productService.countLowStock();
    }

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return userService.findCurrentUserViewByEmail(authentication.getName());
    }
}
