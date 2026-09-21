package com.spai.portal.organization.domain;

import javax.persistence.*;

@Entity @Table(name="team")
public class Team {
    @Id private String id; @Column(nullable=false, unique=true) private String code; @Column(nullable=false) private String name; @Column(nullable=false) private boolean enabled = true;
    public String getId(){return id;} public void setId(String id){this.id=id;} public String getCode(){return code;} public void setCode(String code){this.code=code;} public String getName(){return name;} public void setName(String name){this.name=name;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;}
}
