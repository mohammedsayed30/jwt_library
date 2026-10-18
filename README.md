# JWT Auth Boot Starter

A Spring Boot starter for adding JSON Web Token (JWT) authentication to
Spring MVC applications using Spring Security. It provides a configurable
`JwtService` for generating and validating signed tokens and a servlet filter
that reads bearer tokens from the `Authorization` header.

The starter supplies JWT token handling and request authentication. Your
application remains responsible for user storage, credential verification,
login and logout endpoints, authorization rules, and adding the filter to its
Spring Security filter chain.

## Requirements

- Java 17 or later
- Spring Boot 3.x (the library is currently built with Spring Boot 3.1.5)
- A servlet-based Spring application, such as one using Spring MVC
- An application-provided `UserDetailsService`

## Add the dependency

The current project version is `1.0.0`. To build and install it in your local
Maven repository for development, run this from the library project:

```shell
mvn install
```

Then add the starter to your Spring Boot application's `pom.xml`:

```xml
<dependency>
    <groupId>com.mohammedshaaban.jwtauth</groupId>
    <artifactId>jwt-auth-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

To use the dependency from another project, first publish the release to a
Maven repository accessible to that project. Configure that repository in the
consumer project if it is not available from Maven Central.

### Other dependencies

The starter declares Spring Security, the JJWT API and runtime implementation,
JJWT's Jackson integration, and Spring Boot validation as dependencies. Maven
resolves these transitively, so applications generally do not need to add
them separately.

Your application still needs a servlet web stack, commonly:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

The starter does not provide your application's users, persistence layer,
password encoder, or login endpoint. Add the dependencies needed by your own
implementation (for example, a database driver and Spring Data starter if
users are stored in a database).

## Configure JWT settings

Set the signing secret and token lifetime in the consuming application's
configuration. For example, in `application.yaml`:

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration-time: 24h
```

`jwt.secret` must be non-blank and at least 32 characters. The value is
Base64-decoded and used as an HMAC signing key; use a cryptographically random
secret with at least 32 random bytes. For example, generate a Base64-encoded
32-byte secret with:

```shell
openssl rand -base64 32
```

Keep the secret outside source control. Set it through an environment
variable, secret manager, or another secure configuration mechanism. Tokens
can only be verified by applications configured with the same secret.

`jwt.expiration-time` is a Spring `Duration` value. It defaults to `1d` (one
day) when omitted. Examples include `30m`, `12h`, and `7d`.

## Provide user lookup

The starter registers a `JwtService` bean automatically. It registers a
`JwtAuthenticationFilter` bean only when:

- Spring Security and the servlet API are on the classpath;
- the application is a servlet web application; and
- the application provides a `UserDetailsService`.

Implement `UserDetailsService` to load users by the username stored in the JWT
subject. The service must return the user's current authorities, which the
filter places in the Spring Security authentication:

```java
@Service
public class ApplicationUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public ApplicationUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
```

Replace `UserRepository` and the lookup logic with the user model and
persistence mechanism used by your application.

## Configure the Spring Security filter chain

The starter creates the filter as a bean, but does not add it to your
application's `SecurityFilterChain`. Register it explicitly, usually before
`UsernamePasswordAuthenticationFilter`:

```java
@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

This example assumes a stateless API and disables CSRF protection. Choose CSRF,
session, CORS, endpoint authorization, and exception handling settings to suit
your application; do not copy these choices blindly for a browser application
that uses cookies or server-side sessions.

The filter checks for an `Authorization` header with the `Bearer ` prefix. If
the token parses and validates, it loads the subject through
`UserDetailsService` and sets the Spring Security authentication for the
request. Requests without a bearer token pass through unauthenticated, so your
security rules determine whether they are allowed. Invalid JWT parsing clears
the security context and continues the filter chain; the application should
configure its normal Spring Security entry point to handle unauthenticated
access to protected resources.

## Generate a token after login

The starter does not implement a login endpoint or verify passwords. Your
application must authenticate submitted credentials (for example, using
Spring Security's `AuthenticationManager`) and, after successful
authentication, call `JwtService` to issue a token:

```java
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthenticationController(
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(), request.password()));

        UserDetails user = userDetailsService.loadUserByUsername(request.username());
        return new TokenResponse(jwtService.generateToken(user));
    }

    public record LoginRequest(String username, String password) {
    }

    public record TokenResponse(String accessToken) {
    }
}
```

Configure an `AuthenticationManager` and the application's
`PasswordEncoder`/authentication provider as appropriate for its user store.
Do not return passwords or other credentials in the token response.

For subsequent requests, clients send the token as:

```http
Authorization: Bearer <access-token>
```

## Use `JwtService`

`JwtService` is available as a Spring bean. It supports:

```java
String token = jwtService.generateToken(userDetails);
```

Additional application claims may be included with the overload:

```java
Map<String, Object> claims = Map.of("tenant", tenantId);
String token = jwtService.generateToken(claims, userDetails);
```

The service sets the token subject from `UserDetails.getUsername()`, and adds
issued-at and expiration timestamps. Use `extractUsername`,
`extractClaim`, `isTokenExpired`, and `isTokenValid` to inspect or validate
tokens. Parsing and signature verification errors are reported as JWT
exceptions; callers should handle invalid tokens appropriately.

## What the application must implement

- Store and load users, including their current authorities.
- Provide a `UserDetailsService`.
- Authenticate login credentials and issue tokens from a login endpoint.
- Configure and register the `JwtAuthenticationFilter` in the
  `SecurityFilterChain`.
- Decide which routes are public and which require authentication.
- Choose appropriate session, CSRF, CORS, and error-handling policies.
- Store and rotate the signing secret securely.

The starter does not provide refresh tokens, token revocation/blacklisting,
logout behavior, user registration, password hashing, role/permission
management, or a login API.
