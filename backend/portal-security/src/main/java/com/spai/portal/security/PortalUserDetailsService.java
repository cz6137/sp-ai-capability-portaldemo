package com.spai.portal.security;
import com.spai.portal.organization.repository.AppUserRepository; import org.springframework.security.core.userdetails.*; import org.springframework.stereotype.Service;
@Service public class PortalUserDetailsService implements UserDetailsService { private final AppUserRepository users; public PortalUserDetailsService(AppUserRepository users){this.users=users;} public UserDetails loadUserByUsername(String username){return new PortalPrincipal(users.findByUsername(username).orElseThrow(()->new UsernameNotFoundException(username)));} }
