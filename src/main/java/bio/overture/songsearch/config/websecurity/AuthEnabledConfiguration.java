package bio.overture.songsearch.config.websecurity;

import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import static java.util.stream.Collectors.toList;


@EnableWebFluxSecurity
@Slf4j
@Profile("secure")
public class AuthEnabledConfiguration {

    AuthProperties authProperties;

    ResourceLoader resourceLoader;

    @Autowired
    public AuthEnabledConfiguration(AuthProperties authProperties, ResourceLoader resourceLoader) {
        this.authProperties = authProperties;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(
            ServerHttpSecurity http) {
        http
                .csrf().disable() // graphql endpoint resolves with 403 if csrf is enabled
                .authorizeExchange()
                .pathMatchers("/graphql/**").permitAll() // `hasAuthority` is checked at graphql layer
                .pathMatchers("/actuator/**").permitAll()
            .and()
                .oauth2ResourceServer().jwt()
                    .jwtDecoder(jwtDecoder())
                    .jwtAuthenticationConverter(grantedAuthoritiesExtractor());
        return http.build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(this.jwtToGrantedAuthoritiesConverter);
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

    private final Converter<Jwt, Collection<GrantedAuthority>> jwtToGrantedAuthoritiesConverter = (jwt) -> {
        val scopesBuilder = ImmutableList.<String>builder();

        try {
            val context = (Map<String, Object>) jwt.getClaims().get("context");
            scopesBuilder.addAll((Collection<String>) context.get("scope"));
        } catch (Exception e) {
            log.error("Unable to extract scopes from JWT");
        }

        val scopes = scopesBuilder.build();

        log.info("JWT scopes: " + scopes);

        return scopes.stream()
                       .map(SimpleGrantedAuthority::new)
                       .collect(toList());
    };

    @SneakyThrows
    private ReactiveJwtDecoder jwtDecoder() {
        String publicKeyStr;

        val publicKeyUrl = authProperties.getJwtPublicKeyUrl();
        if (!publicKeyUrl.isBlank()) {
            publicKeyStr = fetchJWTPublicKey(publicKeyUrl);
        } else {
            publicKeyStr = authProperties.getJwtPublicKeyStr();
        }

        val publicKeyContent = publicKeyStr
                                       .replaceAll("\\n", "")
                                       .replace("-----BEGIN PUBLIC KEY-----", "")
                                       .replace("-----END PUBLIC KEY-----", "");

        KeyFactory kf = KeyFactory.getInstance("RSA");

        X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyContent));
        RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(keySpecX509);

        return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();

    }


    /**
     * Call EGO server for public key to use when verifying JWTs
     * Pass this value to the JWTTokenConverter
     */
    @SneakyThrows
    private String fetchJWTPublicKey(String publicKeyUrl) {
        log.info("Fetching EGO public key");
        val publicKeyResource = resourceLoader.getResource(publicKeyUrl);

        val stringBuilder = new StringBuilder();
        val reader = new BufferedReader(
                new InputStreamReader(publicKeyResource.getInputStream()));

        reader.lines().forEach(stringBuilder::append);
        return stringBuilder.toString();
    }

}
