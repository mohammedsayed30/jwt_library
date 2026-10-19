# JWT Auth Boot Starter

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)
![Spring Boot 3.x](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)

A Spring Boot starter that adds JSON Web Token (JWT) authentication to Spring MVC
applications secured with Spring Security.

It gives you two things:

- **`JwtService`**: generates and validates signed tokens.
- **`JwtAuthenticationFilter`**: reads the bearer token from the `Authorization`
  header and authenticates the request.

Your application stays in control of user storage, password verification, login
endpoints, and authorization rules. The starter only handles tokens.

## Table of contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Step 1: Provide user lookup](#step-1-provide-user-lookup)
- [Step 2: Register the filter](#step-2-register-the-filter)
- [Step 3: Create a login endpoint](#step-3-create-a-login-endpoint)
- [Step 4: Call protected endpoints](#step-4-call-protected-endpoints)
- [Using `JwtService` directly](#using-jwtservice-directly)
- [How it works](#how-it-works)
- [What the starter does and does not do](#what-the-starter-does-and-does-not-do)
- [Troubleshooting](#troubleshooting)
- [Security recommendations](#security-recommendations)
- [License](#license)

## Requirements

| Requirement | Details |
|---|---|
| Java | 17 or later |
| Spring Boot | 3.x. Built and tested with **3.1.5**. Other 3.x versions are expected to work but have not been verified. Spring Boot 2.x is not supported (it uses `javax.*`, this library uses `jakarta.*`) |
| Application type | Servlet-based (Spring MVC) |
| Required bean | A `UserDetailsService` provided by your application |

## Installation

The starter is not yet published to a public Maven repository, so install it
from source:

```shell
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd YOUR_REPO
mvn clean install
```

This installs the library into your local Maven repository (`~/.m2`). Then add
it to your application's `pom.xml`:

```xml
<dependency>
    <groupId>com.mohammedshaaban.jwtauth</groupId>
    <artifactId>jwt-auth-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Your application also needs a servlet web stack, usually:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Spring Security, JJWT, and Spring Boot validation come with the starter
transitively, so you do not need to add them yourself.

## Quick start

1. Add the dependency (see above).
2. Set `jwt.secret` in your configuration.
3. Provide a `UserDetailsService` bean.
4. Add `JwtAuthenticationFilter` to your `SecurityFilterChain`.
5. Create a login endpoint that calls `jwtService.generateToken(...)`.

Each step is explained below. Imports are omitted from the code examples for brevity.

## Configuration

Set the properties in the application that uses the starter, for example in
`application.yml`:

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration-time: 24h
```

or in `application.properties`:

```properties
jwt.secret=${JWT_SECRET}
jwt.expiration-time=24h
```

| Property | Required | Default | Description |
|---|---|---|---|
| `jwt.secret` | Yes | none | Base64-encoded signing key. The application fails to start if it is missing or blank |
| `jwt.expiration-time` | No | `1d` | Token lifetime as a Spring `Duration`, for example `30m`, `12h`, `7d` |

### Generating a secret

The secret is Base64-decoded and used as an HMAC signing key. HS256 needs a key
of at least 256 bits, so the decoded key must be **at least 32 bytes**. Generate
a suitable one with:

```shell
openssl rand -base64 32
```

This produces a 44-character string. Note that a Base64 string of only 32
characters decodes to just 24 bytes, which is too short, so always generate the
secret with the command above instead of typing one by hand.

Keep the secret out of source control. Provide it through an environment
variable, a secret manager, or another secure mechanism. Tokens can only be
verified by applications that use the same secret.

## Step 1: Provide user lookup

Implement `UserDetailsService`. It must load users by the username stored in the
token subject and return their **current** authorities, which the filter places
in the Spring Security authentication.

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

Your user entity can implement `UserDetails` directly, as above. If you prefer
to keep your entity free of Spring Security types, map it to Spring's builder
instead:

```java
return org.springframework.security.core.userdetails.User
        .withUsername(account.getEmail())
        .password(account.getPassword())
        .authorities(account.getRole())
        .build();
```

The class name and your entity name are up to you. The starter only needs a
bean of type `UserDetailsService`. If your application has more than one, mark
one with `@Primary`.

## Step 2: Register the filter

The starter creates `JwtAuthenticationFilter` as a bean, but it does **not** add
it to your security chain. Add it yourself, before
`UsernamePasswordAuthenticationFilter`:

```java
@Configuration
@EnableWebSecurity
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

This example assumes a stateless API and disables CSRF protection. Choose your
CSRF, session, CORS, and endpoint rules to match your application. Do not copy
these choices blindly into a browser application that uses cookies or
server-side sessions.

You can also return a JSON error for unauthenticated requests by configuring an
`authenticationEntryPoint` in `exceptionHandling(...)`.

## Step 3: Create a login endpoint

The starter does not verify passwords or provide a login endpoint. Your
application authenticates the credentials and then asks `JwtService` for a
token.

First, expose the beans Spring Security needs for password login:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

@Bean
AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

When your application has one `UserDetailsService` bean and a `PasswordEncoder`
bean, Spring Security builds the password-checking provider automatically.

Then write the endpoint:

```java
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthenticationController(AuthenticationManager authenticationManager,
                                    JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails user = (UserDetails) authentication.getPrincipal();
        return new TokenResponse(jwtService.generateToken(user));
    }

    public record LoginRequest(String username, String password) {}

    public record TokenResponse(String accessToken) {}
}
```

Never return passwords or other credentials in the response. Return a small
response object like the one above instead of your whole user entity.

## Step 4: Call protected endpoints

Log in and read the token:

```shell
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user@example.com","password":"secret"}'
```

Send the token on later requests:

```shell
curl http://localhost:8080/api/profile \
  -H "Authorization: Bearer <access-token>"
```

## Using `JwtService` directly

`JwtService` is a Spring bean, so you can inject it anywhere.

Generate a token from a `UserDetails`:

```java
String token = jwtService.generateToken(userDetails);
```

Add your own claims:

```java
Map<String, Object> claims = Map.of("tenant", tenantId);
String token = jwtService.generateToken(claims, userDetails);
```

The service sets the subject from `UserDetails.getUsername()` and adds the
issued-at and expiration timestamps. To read or validate tokens, use
`extractUsername`, `extractClaim`, `isTokenExpired`, and `isTokenValid`.
Parsing and signature errors are reported as JWT exceptions (`JwtException`),
so handle invalid tokens in your code.

Do not put sensitive data in claims. A JWT payload is only signed, not
encrypted, so anyone who holds the token can read it.

## How it works

```
Client                      Your application
  |                                |
  |-- POST /auth/login ----------->|  AuthenticationManager checks the password
  |<------ access token -----------|  JwtService.generateToken(user)
  |                                |
  |-- GET /api/... --------------->|
  |   Authorization: Bearer <jwt>  |
  |                                |  JwtAuthenticationFilter:
  |                                |   1. reads the Bearer token
  |                                |   2. verifies signature and expiry
  |                                |   3. loads the user with UserDetailsService
  |                                |   4. sets the authentication for this request
  |<------ response ---------------|  your authorization rules decide the outcome
```

The filter behaves as follows:

- Requests without a `Bearer` token pass through unauthenticated. Your security
  rules decide whether they are allowed, so public endpoints keep working.
- If a token fails to parse or validate, the filter clears the security context
  and continues the chain. Protected endpoints then reject the request through
  your normal Spring Security entry point.
- The filter runs on every request, including public ones. An invalid token on a
  public endpoint is ignored rather than rejected.

The starter registers `JwtService` automatically. It registers
`JwtAuthenticationFilter` only when Spring Security and the servlet API are on
the classpath, the application is a servlet web application, and a
`UserDetailsService` bean exists. You can replace either bean by defining your
own bean of the same type.

## What the starter does and does not do

**The starter provides:**

- Token generation and validation (`JwtService`)
- Request authentication from the `Authorization` header (`JwtAuthenticationFilter`)
- Configuration through `jwt.*` properties, with autocomplete in IDEs that read
  Spring configuration metadata

**Your application must provide:**

- User storage and a `UserDetailsService`
- Credential verification and the login endpoint
- Registration of the filter in the `SecurityFilterChain`
- Public and protected route rules
- CSRF, CORS, session, and error-handling policies
- Secure storage and rotation of the signing secret

**Not included:** refresh tokens, token revocation or blacklisting, logout,
user registration, password hashing, role and permission management, and
reactive (WebFlux) support.

## Troubleshooting

| Problem | Likely cause and fix |
|---|---|
| `No qualifying bean of type JwtAuthenticationFilter` | The filter is only created when the app is a servlet web app and a `UserDetailsService` bean exists. Add one. Run with `--debug` and check the conditions report for the starter's auto-configuration |
| Valid token, but protected endpoints return 401 or 403 | The filter was not added to the `SecurityFilterChain`. Add `.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)` |
| Application fails to start with a `jwt.secret` binding error | `jwt.secret` is missing, blank, or too short. Generate one with `openssl rand -base64 32` |
| `WeakKeyException` | The secret decodes to fewer than 32 bytes. Generate it with `openssl rand -base64 32` |
| Tokens stop working after a restart or on another instance | The secret changed or differs between instances. Use the same fixed `jwt.secret` everywhere |
| `NoUniqueBeanDefinitionException` for `UserDetailsService` | Your application has more than one. Mark one with `@Primary` |
| Compile or startup errors mentioning `javax.servlet` | You are on Spring Boot 2.x. This starter needs Spring Boot 3.x (`jakarta.*`) |
| Token rejected as expired | Increase `jwt.expiration-time`, or have the client log in again |

## Security recommendations

- Always serve your API over HTTPS. A bearer token can be used by anyone who
  obtains it.
- Keep token lifetimes short, and keep the signing secret out of source control.
- Rotate the secret if you suspect it has leaked. All existing tokens become
  invalid when you do.
- Use the same error message and status for an unknown user and a wrong password
  at login, so attackers cannot discover which accounts exist.
- Because there is no revocation, a stolen token stays valid until it expires.
  Plan for this if your application needs immediate logout.

## License

This project is licensed under the [MIT License](LICENSE).