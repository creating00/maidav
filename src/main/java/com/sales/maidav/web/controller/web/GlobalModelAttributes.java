package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.user.UserService;
import com.sales.maidav.web.dto.CurrentUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request == null ? "" : request.getRequestURI();
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null
                && !authentication.getName().isBlank();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("formError", "La imagen es demasiado pesada. Proba con una foto mas liviana o comprimida.");
        RequestContextUtils.getFlashMapManager(request).saveOutputFlashMap(flashMap, request, response);

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            return "redirect:" + referer;
        }

        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.matches(".*/products/\\d+$")) {
            return "redirect:" + requestUri + "/edit";
        }
        return "redirect:/products/new";
    }
}
