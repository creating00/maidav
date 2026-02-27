package com.sales.maidav.repository.user;

import com.sales.maidav.model.user.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
}

