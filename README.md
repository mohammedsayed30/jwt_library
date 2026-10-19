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
| Spring Boot | 3.x. Built and tested with **3.1.5**. Other later versions are expected to work.|
| Application type | Servlet-based (Spring MVC) |
| Required bean | A `UserDetailsService` provided by your application |

## Installation

The starter is not yet published to a public Maven repository, so install it
from source:

```shell
git clone https://github.com/mohammedsayed30/jwt_library.git
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

On every request, the filter reads the user identifier from the token and asks your application to load that user. You provide this by registering a bean of type `UserDetailsService`. The starter only needs that bean, so you are free to choose how your user model and service are built.

### The identifier does not have to be a "username"

The method is named `loadUserByUsername`, but the value it receives can be **any value that uniquely identifies a user**: an email address, a phone number, a username, an employee number, or a UUID.

The identifier is chosen in one place. When a token is generated, the starter stores `UserDetails.getUsername()` as the token subject. On later requests, the filter passes that same subject to `loadUserByUsername(...)`. So whatever `getUsername()` returns is what your lookup receives. Follow these rules:

- **The identifier must be unique**: two users must never share the same value.
- **It must be stable**: if a user changes their email, tokens issued with the old email no longer match any user. Prefer a value that rarely changes, or expect users to log in again.
- **`getUsername()` and the lookup must use the same field**: if `getUsername()` returns the email, `loadUserByUsername` must search by email.
- **Normalize it**: for emails, store and search in lowercase, so `User@Mail.com` and `user@mail.com` are not treated as different people.

### Where to implement `UserDetailsService`

**Option 1: your existing user service implements it** (the simplest):

```java
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
```

**Option 2: a dedicated class** (recommended when your user service has other dependencies):

```java
@Service
public class ApplicationUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public ApplicationUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) {
        return userRepository.findByEmail(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
```

The parameter name does not matter (`username`, `email`, `identifier`). Only the method signature is fixed.

If your user service also depends on beans that depend on `UserDetailsService` (for example a `PasswordEncoder` or `AuthenticationManager` used during login), you can get a circular dependency error. A dedicated class (Option 2) avoids this.

### How to produce the `UserDetails` object

`loadUserByUsername` must return a `UserDetails`. There are three ways to build it. Choose the one that fits your project.

#### Option A: your entity implements `UserDetails`

Your user entity implements the interface itself, so it is returned directly from the lookup.

```java
@Entity
public class User implements UserDetails {

    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    private String password;

    private String role;   // for example "USER" or "ADMIN"

    @Override
    public String getUsername() {
        return email;      // the unique identifier that goes into the token
    }

    @Override
    public String getPassword() {
        return password;   // the hashed password
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }

    // other getters and setters
}
```

Good for: small projects. Your whole user object is available in controllers (`@AuthenticationPrincipal User user`), including fields like `id` and `fullName`.

Trade-off: your entity now carries security concerns. Mark the `password` field with `@JsonIgnore` so it is never included if you return the entity in a JSON response.

#### Option B: map your entity to Spring's built-in `User`

Your entity stays a plain class with no Spring Security interface. You convert it inside `loadUserByUsername`.

#### Note:Account in the below sections mean the User Model (adjust the name based on you User model name)

```java
@Override
public UserDetails loadUserByUsername(String email) {
    Account account = accountRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    return org.springframework.security.core.userdetails.User
            .withUsername(account.getEmail())
            .password(account.getPassword())
            .authorities("ROLE_" + account.getRole())
            .build();
}
```

Good for: keeping your entity clean and independent of Spring Security.

Trade-off: the principal in the security context is Spring's `User`, not your entity. Controllers can read the identifier and roles but not your custom fields (such as `id`), unless they load the entity again.

You can also set the status flags with the builder: `.disabled(!account.isActive())`, `.accountLocked(account.isLocked())`, `.accountExpired(...)`, and `.credentialsExpired(...)`.

#### Option C: your own `UserDetails` wrapper class

A separate class implements `UserDetails` and wraps your entity. It gives you Option A's custom fields without putting security code in the entity.

```java
public class ApplicationUser implements UserDetails {

    private final Account account;

    public ApplicationUser(Account account) {
        this.account = account;
    }

    public Long getId() {                      // your own extra method
        return account.getId();
    }

    @Override public String getUsername() { return account.getEmail(); }
    @Override public String getPassword() { return account.getPassword(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole()));
    }

    @Override public boolean isEnabled() { return account.isActive(); }
    @Override public boolean isAccountNonLocked() { return !account.isLocked(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
```

```java
@Override
public UserDetails loadUserByUsername(String email) {
    Account account = accountRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    return new ApplicationUser(account);
}
```

Good for: larger projects. Controllers can use `@AuthenticationPrincipal ApplicationUser user` and call `user.getId()`.

### Which option should I choose?

| | Entity implements `UserDetails` (A) | Spring `User` builder (B) | Own wrapper class (C) |
|---|---|---|---|
| Extra classes | None | None | One |
| Entity stays free of Spring Security | No | Yes | Yes |
| Access to your custom fields in controllers | Yes | No | Yes |
| Best for | Small projects, quick start | Simple apps, clean entities | Larger or long-lived projects |

All three work with the starter in the same way.

### What each `UserDetails` method is used for

| Method | Purpose |
|---|---|
| `getUsername()` | The unique identifier. **Stored as the token subject** and passed back to `loadUserByUsername` on every request |
| `getPassword()` | The **hashed** password. Used by Spring Security when verifying credentials at login. Never store or return plain text |
| `getAuthorities()` | The user's roles and permissions. The filter places them in the security context, so rules such as `hasRole("ADMIN")` work |
| `isEnabled()` | Whether the account is active |
| `isAccountNonLocked()` | Whether the account is not locked |
| `isAccountNonExpired()` | Whether the account has not expired |
| `isCredentialsNonExpired()` | Whether the password has not expired |

The last four methods are account-status flags. Spring Security checks them when you authenticate credentials through `AuthenticationManager` at **login**. The starter's filter does **not** check them on requests that carry a token. If you need an account to be rejected immediately, even for tokens already issued, make `loadUserByUsername` throw `UsernameNotFoundException` for that account (for example, when it is disabled). The filter then treats the request as unauthenticated. If you do not use these features, return `true` from all four.

For role-based rules, note that `hasRole("ADMIN")` looks for an authority named `ROLE_ADMIN`, while `hasAuthority("ADMIN")` looks for exactly `ADMIN`. Add the `ROLE_` prefix when you create role authorities.

### Requirements for your implementation

- Register exactly one `UserDetailsService` bean. If your application has more than one, mark the one the starter should use with `@Primary`.
- Throw `UsernameNotFoundException` when the user does not exist. Do not return `null`.
- Return the user's **current** authorities. The user is loaded on every request, so changes to a user's roles take effect immediately, even for tokens issued earlier.
- Use the same identifier in `getUsername()` and in the lookup (see the rules above).

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