package com.spai.portal.security;

import com.spai.portal.common.ApiResponse; import com.spai.portal.common.TraceIdFilter; import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*; import org.springframework.http.HttpStatus; import org.springframework.security.authentication.*; import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.config.http.SessionCreationPolicy; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.security.web.SecurityFilterChain; import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration @EnableGlobalMethodSecurity(prePostEnabled=true)
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean AuthenticationManager authenticationManager(org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
    @Bean SecurityFilterChain chain(HttpSecurity http,JwtService jwt,PortalUserDetailsService users,ObjectMapper mapper)throws Exception{
        http.csrf().disable().cors().and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and().authorizeRequests()
          .antMatchers("/api/v1/auth/**","/actuator/health","/v3/api-docs/**","/swagger-ui/**").permitAll().antMatchers("/api/**").authenticated().anyRequest().permitAll().and()
          .exceptionHandling().authenticationEntryPoint((req,res,e)->{res.setStatus(HttpStatus.UNAUTHORIZED.value());res.setContentType("application/json;charset=UTF-8");mapper.writeValue(res.getWriter(),ApiResponse.error("UNAUTHORIZED","请先登录"));});
        http.addFilterBefore(new TraceIdFilter(),UsernamePasswordAuthenticationFilter.class).addFilterBefore(new JwtAuthenticationFilter(jwt,users),UsernamePasswordAuthenticationFilter.class); return http.build();
    }
}
