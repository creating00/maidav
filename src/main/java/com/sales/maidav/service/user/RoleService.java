package com.sales.maidav.service.user;

import com.sales.maidav.model.user.Role;

import java.util.List;

public interface RoleService {

    List<Role> findAll();

    Role findById(Long id);

    Role create(Role role);

    Role update(Long id, Role role);

    void delete(Long id);

    long count();
}

