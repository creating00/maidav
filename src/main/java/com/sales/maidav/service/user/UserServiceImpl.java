package com.sales.maidav.service.user;

import com.sales.maidav.model.user.Role;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.user.RoleRepository;
import com.sales.maidav.repository.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Override
    public User create(User user, List<Long> roleIds) {

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        Set<Role> roles = new HashSet<>(roleRepository.findByIdIn(roleIds));
        user.setRoles(roles);

        return userRepository.save(user);
    }

    @Override
    public User update(Long id, User data, List<Long> roleIds) {

        User user = findById(id);

        user.setEmail(data.getEmail());
        user.setEnabled(data.isEnabled());
        user.setFirstName(data.getFirstName());
        user.setLastName(data.getLastName());
        user.setPhone(data.getPhone());
        user.setAddress(data.getAddress());
        user.setBirthDate(data.getBirthDate());
        user.setPhotoPath(data.getPhotoPath());

        if (data.getPassword() != null && !data.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(data.getPassword()));
        }

        Set<Role> roles = new HashSet<>(roleRepository.findByIdIn(roleIds));
        user.setRoles(roles);

        return user;
    }

    @Override
    public User updateProfile(User data) {
        User user = findById(data.getId());
        user.setFirstName(data.getFirstName());
        user.setLastName(data.getLastName());
        user.setPhone(data.getPhone());
        user.setAddress(data.getAddress());
        user.setBirthDate(data.getBirthDate());
        user.setPhotoPath(data.getPhotoPath());
        return user;
    }

    @Override
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public long count() {
        return userRepository.count();
    }

    @Override
    public List<User> findByRoleName(String roleName) {
        return userRepository.findByRoles_Name(roleName);
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
