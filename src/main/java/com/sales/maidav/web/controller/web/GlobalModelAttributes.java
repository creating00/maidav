package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.user.UserService;
import com.sales.maidav.web.dto.CurrentUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final ObjectProvider<ProductService> productServiceProvider;
    private final ObjectProvider<UserService> userServiceProvider;

    @ModelAttribute("lowStockCount")
    public long lowStockCount(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return 0;
        }
        ProductService productService = productServiceProvider.getIfAvailable();
        return productService == null ? 0 : productService.countLowStock();
    }

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }
        UserService userService = userServiceProvider.getIfAvailable();
        return userService == null ? null : userService.findCurrentUserViewByEmail(authentication.getName());
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null
                && !authentication.getName().isBlank();
    }
}
