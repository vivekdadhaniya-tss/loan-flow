package com.loanflow.repository;

import com.loanflow.entity.user.User;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UserRepository extends JpaRepository<User , UUID> {

    Optional<User>  findByEmail(String email);

    boolean existsByEmail(String email);
}
