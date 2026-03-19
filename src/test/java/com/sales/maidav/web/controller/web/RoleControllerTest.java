package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.user.Permission;
import com.sales.maidav.model.user.Role;
import com.sales.maidav.repository.user.PermissionRepository;
import com.sales.maidav.repository.user.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;

    private RoleController roleController;

    @BeforeEach
    void setUp() {
        roleController = new RoleController(roleRepository, permissionRepository);
    }

    @Test
    void updateReplacesPermissionsInsteadOfKeepingOldOnes() {
        Permission oldPermission = permission(1L, "PROVIDER_READ");
        Permission newPermission = permission(2L, "CLIENT_READ");

        Role existingRole = new Role("VENTAS");
        existingRole.setId(5L);
        existingRole.setPermissions(new java.util.HashSet<>(Set.of(oldPermission)));

        Role incoming = new Role("VENTAS");

        when(roleRepository.findWithPermissionsById(5L)).thenReturn(Optional.of(existingRole));
        when(permissionRepository.findAllById(List.of(2L))).thenReturn(List.of(newPermission));

        String response = roleController.update(5L, incoming, List.of(2L));

        assertThat(response).isEqualTo("redirect:/roles");
        assertThat(existingRole.getPermissions()).containsExactly(newPermission);
        verify(roleRepository).save(existingRole);
    }

    @Test
    void updateAllowsRemovingAllPermissions() {
        Permission oldPermission = permission(1L, "PROVIDER_READ");

        Role existingRole = new Role("VENTAS");
        existingRole.setId(7L);
        existingRole.setPermissions(new java.util.HashSet<>(Set.of(oldPermission)));

        Role incoming = new Role("VENTAS");

        when(roleRepository.findWithPermissionsById(7L)).thenReturn(Optional.of(existingRole));

        roleController.update(7L, incoming, null);

        assertThat(existingRole.getPermissions()).isEmpty();
        verify(roleRepository).save(any(Role.class));
    }

    private Permission permission(Long id, String name) {
        Permission permission = new Permission(name);
        permission.setId(id);
        return permission;
    }
}
