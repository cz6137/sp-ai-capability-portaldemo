package com.spai.portal.organization.domain;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity @Table(name="refresh_token")
public class RefreshToken {
    @Id private String id; @Column(name="user_id",nullable=false) private String userId; @Column(name="token_hash",nullable=false,unique=true) private String tokenHash; @Column(name="expires_at",nullable=false) private OffsetDateTime expiresAt; @Column(name="revoked_at") private OffsetDateTime revokedAt;
    public String getId(){return id;} public void setId(String id){this.id=id;} public String getUserId(){return userId;} public void setUserId(String userId){this.userId=userId;} public String getTokenHash(){return tokenHash;} public void setTokenHash(String tokenHash){this.tokenHash=tokenHash;} public OffsetDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(OffsetDateTime expiresAt){this.expiresAt=expiresAt;} public OffsetDateTime getRevokedAt(){return revokedAt;} public void setRevokedAt(OffsetDateTime revokedAt){this.revokedAt=revokedAt;}
}
