package com.unisen.sgp.service;

import com.unisen.sgp.model.dto.LoginRequest;
import com.unisen.sgp.model.dto.LoginResponse;
import com.unisen.sgp.model.dto.UsuarioResponse;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.security.JwtUtil;
import com.unisen.sgp.security.UsuarioPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthService(AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Valida las credenciales y emite un JWT.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException si el correo
     *         no existe o la contraseña no coincide (indistinguibles a propósito)
     * @throws org.springframework.security.authentication.DisabledException        si la cuenta
     *         está deshabilitada (solo se informa tras validar la contraseña)
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        Usuario.normalizarEmail(request.email()), request.password()));

        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal);
        return LoginResponse.bearer(token, jwtUtil.getExpiration(), UsuarioResponse.from(principal));
    }
}
