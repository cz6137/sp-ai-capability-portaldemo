package com.spai.portal.security;

import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final PrivateKey privateKey; private final PublicKey publicKey; private final long accessSeconds;
    public JwtService(@Value("${portal.jwt.private-key:}") String privatePem,@Value("${portal.jwt.public-key:}") String publicPem,@Value("${portal.jwt.access-seconds:900}") long accessSeconds){this.accessSeconds=accessSeconds;try{if(privatePem.trim().isEmpty()||publicPem.trim().isEmpty()){KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();this.privateKey=pair.getPrivate();this.publicKey=pair.getPublic();}else{KeyFactory factory=KeyFactory.getInstance("RSA");this.privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(decode(privatePem)));this.publicKey=factory.generatePublic(new X509EncodedKeySpec(decode(publicPem)));}}catch(GeneralSecurityException e){throw new IllegalArgumentException("Invalid RSA JWT key configuration",e);}}
    public String issue(PortalPrincipal p){Instant now=Instant.now();return Jwts.builder().setSubject(p.getUsername()).claim("uid",p.getUserId()).claim("team",p.getTeamId()).setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(accessSeconds))).signWith(privateKey,SignatureAlgorithm.RS256).compact();}
    public String username(String token){return Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(token).getBody().getSubject();}
    private byte[] decode(String pem){String clean=pem.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");return Base64.getDecoder().decode(clean);}
}
