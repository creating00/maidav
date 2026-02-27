package com.sales.maidav.service.user;

import com.sales.maidav.model.user.Permission;

import java.util.List;

public interface PermissionService {

    List<Permission> findAll();

    Permission findById(Long id);

    Permission create(Permission permission);

    Permission update(Long id, Permission permission);

    void delete(Long id);

    long count();
}

