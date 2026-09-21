package com.spai.portal.common;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class TraceIdFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String id = TraceIds.begin(request.getHeader("X-Trace-Id")); response.setHeader("X-Trace-Id", id);
        try { chain.doFilter(request, response); } finally { TraceIds.clear(); }
    }
}
