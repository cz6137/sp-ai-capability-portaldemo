package com.spai.portal.security;

import java.util.Collection;
import java.util.stream.Collectors;
import com.spai.portal.organization.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class PortalPrincipal implements UserDetails {
    private final AppUser user;
    public PortalPrincipal(AppUser user){this.user=user;}
    public String getUserId(){return user.getId();} public String getTeamId(){return user.getTeamId();} public String getDisplayName(){return user.getDisplayName();}
    public Collection<? extends GrantedAuthority> getAuthorities(){return user.getRoles().stream().map(r->new SimpleGrantedAuthority("ROLE_"+r)).collect(Collectors.toList());}
    public String getPassword(){return user.getPasswordHash();} public String getUsername(){return user.getUsername();}
    public boolean isAccountNonExpired(){return true;} public boolean isAccountNonLocked(){return user.getLockedUntil()==null||user.getLockedUntil().isBefore(java.time.OffsetDateTime.now());} public boolean isCredentialsNonExpired(){return true;} public boolean isEnabled(){return user.isEnabled();}
}
