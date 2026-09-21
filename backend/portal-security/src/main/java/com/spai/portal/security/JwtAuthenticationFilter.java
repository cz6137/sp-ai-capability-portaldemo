package com.spai.portal.security;

import java.io.IOException; import javax.servlet.*; import javax.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.security.core.userdetails.UserDetails; import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt; private final PortalUserDetailsService users;
    public JwtAuthenticationFilter(JwtService jwt,PortalUserDetailsService users){this.jwt=jwt;this.users=users;}
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String header=request.getHeader("Authorization");
        if(header!=null&&header.startsWith("Bearer ")&&SecurityContextHolder.getContext().getAuthentication()==null){try{UserDetails user=users.loadUserByUsername(jwt.username(header.substring(7)));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}catch(RuntimeException ignored){}}
        chain.doFilter(request,response);
    }
}
