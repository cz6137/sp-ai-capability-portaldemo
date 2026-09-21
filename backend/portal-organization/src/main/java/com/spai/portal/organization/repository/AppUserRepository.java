package com.spai.portal.organization.repository;
import com.spai.portal.organization.domain.AppUser; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface AppUserRepository extends JpaRepository<AppUser,String>{ Optional<AppUser> findByUsername(String username); }
