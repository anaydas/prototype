package org.anay.service;

import org.anay.dao.UserRepository;
import org.anay.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    @Autowired private UserRepository userRepository;

    @Transactional // No readOnly, so hits MASTER
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true) // readOnly=true, so hits REPLICA
    public User getUser(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}