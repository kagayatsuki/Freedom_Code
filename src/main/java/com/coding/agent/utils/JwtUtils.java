package com.coding.agent.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class JwtUtils {
    // 💡 签名密钥：生产环境建议从 application.yml 中读取。必须大于 32 个字符（256位），否则会报错
    private static final String SECRET_KEY_STR = "your-super-secret-key-for-aicoder-demo-project-maimai";
    private static final Key SIGNING_KEY = Keys.hmacShaKeyFor(SECRET_KEY_STR.getBytes());

    // 💡 过期时间：设置为 24 小时 (单位：毫秒)
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000L;

    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRE_TIME);

        // 放入自定义的 Claims 载荷
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
 
        return Jwts.builder()
                .setClaims(claims)                     // 自定义数据
                .setSubject(username)                  // 面向的主体
                .setIssuedAt(now)                      // 签发时间
                .setExpiration(expiration)              // 过期时间
                .signWith(SIGNING_KEY, SignatureAlgorithm.HS256) // 签名算法
                .compact();
    }
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(SIGNING_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.error("JWT Token 已过期: {}", e.getMessage());
            throw new RuntimeException("登录已过期，请重新登录");
        } catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            log.error("JWT Token 格式非法: {}", e.getMessage());
            throw new RuntimeException("无效的身份令牌");
        } catch (SignatureException e) {
            log.error("JWT 签名验证失败: {}", e.getMessage());
            throw new RuntimeException("身份篡改风险，令牌无效");
        }
    }
    public static Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.get("userId").toString());
    }
}
