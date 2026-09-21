package com.spai.portal.security;

import java.time.Duration; import java.util.*; import javax.servlet.http.*; import javax.validation.Valid; import javax.validation.constraints.NotBlank;
import com.spai.portal.common.ApiResponse; import org.springframework.http.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth") public class AuthController {
    private final AuthService service; public AuthController(AuthService service){this.service=service;}
    @PostMapping("/login") public ApiResponse<Map<String,Object>> login(@Valid @RequestBody Login req,HttpServletRequest request,HttpServletResponse res){AuthService.Tokens t=service.login(req.username,req.password);cookie(request,res,t.refreshToken,7*24*3600);return ApiResponse.ok(userToken(t));}
    @PostMapping("/refresh") public ApiResponse<Map<String,Object>> refresh(@CookieValue(value="refresh_token",required=false)String refresh,HttpServletRequest request,HttpServletResponse res){AuthService.Tokens t=service.refresh(refresh);cookie(request,res,t.refreshToken,7*24*3600);return ApiResponse.ok(userToken(t));}
    @PostMapping("/logout") public ApiResponse<Void> logout(@CookieValue(value="refresh_token",required=false)String refresh,HttpServletRequest request,HttpServletResponse res){service.logout(refresh);cookie(request,res,"",0);return ApiResponse.ok(null);}
    @GetMapping("/me") public ApiResponse<Map<String,Object>> me(@AuthenticationPrincipal PortalPrincipal p){return ApiResponse.ok(user(p));}
    private Map<String,Object> userToken(AuthService.Tokens t){Map<String,Object> m=user(t.principal);m.put("accessToken",t.accessToken);m.put("expiresIn",900);return m;}
    private Map<String,Object> user(PortalPrincipal p){Map<String,Object> m=new LinkedHashMap<String,Object>();m.put("id",p.getUserId());m.put("username",p.getUsername());m.put("displayName",p.getDisplayName());m.put("teamId",p.getTeamId());m.put("roles",p.getAuthorities());return m;}
    private void cookie(HttpServletRequest request,HttpServletResponse res,String value,int age){String path=request.getContextPath();if(path==null||path.isEmpty())path="/";res.addHeader("Set-Cookie","refresh_token="+value+"; Max-Age="+age+"; Path="+path+"; HttpOnly; SameSite=Lax");}
    public static class Login {@NotBlank public String username;@NotBlank public String password;}
}
