package com.rozetka.security;

import com.rozetka.entity.AppUserRole;
import com.rozetka.exception.CustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.rozetka.exception.CustomException.EXPIRED_OR_INVALID_TOKEN;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${spring.security.jwt.token.secret-key}")
    private String secretKey;

    @Value("${spring.security.jwt.token.expire-length}")
    private long validityInMilliseconds = 3600000;

    private final MyUserDetails myUserDetails;

    @PostConstruct
    protected void init() {

        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    public String createToken(String username, List<AppUserRole> appUserRoles) {

        Claims claims = Jwts.claims().setSubject(username);

        claims.put("auth", appUserRoles.stream()
                .map(s -> new SimpleGrantedAuthority(s.getAuthority())).filter(Objects::nonNull)
                .collect(Collectors.toList()));

        Date now = new Date();

        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public Authentication getAuthentication(String token) {

        UserDetails userDetails = myUserDetails.loadUserByUsername(getUsername(token));

        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String getUsername(String token) {

        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
    }

    public String resolveToken(HttpServletRequest req) {

        String bearerToken = req.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {

            return bearerToken.substring(7);
        }

        return null;
    }

    public boolean validateToken(String token) {

        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);

            return true;

        } catch (JwtException | IllegalArgumentException e) {

            throw new CustomException(EXPIRED_OR_INVALID_TOKEN, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
