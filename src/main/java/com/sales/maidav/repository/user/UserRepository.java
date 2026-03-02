package com.sales.maidav.repository.user;

import com.sales.maidav.model.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    List<User> findAll();

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    @Query("""
            select distinct u
            from User u
            left join fetch u.roles r
            left join fetch r.permissions
            where u.email = :email
            """)
    Optional<User> findByEmailWithRolesAndPermissions(String email);

    @Query("""
            select u.photoPath
            from User u
            where u.email = :email
            """)
    Optional<String> findPhotoPathByEmail(String email);

    List<User> findByRoles_Name(String name);
}
