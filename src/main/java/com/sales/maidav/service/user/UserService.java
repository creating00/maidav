package com.sales.maidav.service.user;

import com.sales.maidav.model.user.User;

import java.util.List;

public interface UserService {

    List<User> findAll();

    User findById(Long id);

    User create(User user, List<Long> roleIds);

    User update(Long id, User user, List<Long> roleIds);
    User updateProfile(User user);

    void delete(Long id);
    long count();

    List<User> findByRoleName(String roleName);

    User findByEmail(String email);
}

