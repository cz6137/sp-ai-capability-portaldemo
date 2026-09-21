package com.spai.portal.organization.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity @Table(name="app_user")
public class AppUser {
    @Id private String id;
    @Column(nullable=false,unique=true) private String username;
    @JsonIgnore @Column(name="password_hash",nullable=false) private String passwordHash;
    @Column(name="display_name",nullable=false) private String displayName;
    @Column(name="team_id") private String teamId;
    @Column(nullable=false) private boolean enabled=true;
    @Column(name="failed_attempts",nullable=false) private int failedAttempts;
    @Column(name="locked_until") private OffsetDateTime lockedUntil;
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="user_role",joinColumns=@JoinColumn(name="user_id")) @Column(name="role_code") private Set<String> roles=new LinkedHashSet<String>();
    public String getId(){return id;} public void setId(String id){this.id=id;} public String getUsername(){return username;} public void setUsername(String username){this.username=username;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String passwordHash){this.passwordHash=passwordHash;} public String getDisplayName(){return displayName;} public void setDisplayName(String displayName){this.displayName=displayName;} public String getTeamId(){return teamId;} public void setTeamId(String teamId){this.teamId=teamId;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;} public int getFailedAttempts(){return failedAttempts;} public void setFailedAttempts(int failedAttempts){this.failedAttempts=failedAttempts;} public OffsetDateTime getLockedUntil(){return lockedUntil;} public void setLockedUntil(OffsetDateTime lockedUntil){this.lockedUntil=lockedUntil;} public Set<String> getRoles(){return roles;} public void setRoles(Set<String> roles){this.roles=roles;}
}
