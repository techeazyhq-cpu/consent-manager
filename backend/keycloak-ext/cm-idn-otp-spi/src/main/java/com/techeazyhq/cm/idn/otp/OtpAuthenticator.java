/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

package com.techeazyhq.cm.idn.otp;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.BruteForceProtector;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Passwordless sign-in (plan §9a): the person gives an email address or phone number, receives a 6-digit code through
 * cm-ntf-svc, and types it in. It replaces the username/password step of each realm's browser flow, so CM Admin, the DF
 * app and the DP portal all sign in the same way.
 *
 * <ul>
 *   <li>A code is valid for {@code codeTtlSeconds} and allows {@code maxAttempts} wrong guesses; a new code can be
 *       requested after {@value #RESEND_INTERVAL_SECONDS} seconds, at most {@value #MAX_SENDS} times per sign-in.</li>
 *   <li>Wrong guesses for an existing account are reported to Keycloak's brute-force protection, so a locked account
 *       stays locked whichever sign-in the guesses came from.</li>
 *   <li>The page after "Send code" looks the same whether or not the address has an account, so it can't be used to
 *       find out who is registered. Proving an unknown address creates an account with {@code autoRegisterRole}
 *       (DP by default): a Data Principal signs up just by signing in. Leave the role empty to allow existing
 *       accounts only.</li>
 * </ul>
 */
public class OtpAuthenticator implements Authenticator {

    static final String IDENTIFY_FORM = "cm-otp-identify.ftl";
    static final String CODE_FORM = "cm-otp-code.ftl";
    static final String CFG_NTF_BASE_URL = "ntfBaseUrl";
    static final String CFG_CODE_TTL_SECONDS = "codeTtlSeconds";
    static final String CFG_MAX_ATTEMPTS = "maxAttempts";
    static final String CFG_AUTO_REGISTER_ROLE = "autoRegisterRole";
    static final String DEFAULT_NTF_BASE_URL = "http://cm-ntf-svc:8084";
    static final int RESEND_INTERVAL_SECONDS = 30;
    static final int MAX_SENDS = 3;
    static final String PHONE_ATTRIBUTE = "phone";

    private static final Logger LOG = Logger.getLogger(OtpAuthenticator.class);
    private static final String NOTE_KIND = "cm.otp.kind";
    private static final String NOTE_VALUE = "cm.otp.value";

    private final NtfOtpSender sender;

    public OtpAuthenticator(NtfOtpSender sender) {
        this.sender = sender;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        clear(context.getAuthenticationSession());
        context.challenge(identifyForm(context, null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        if (form.containsKey("restart")) {
            authenticate(context);
        } else if (form.containsKey("identifier")) {
            start(context, form.getFirst("identifier"));
        } else if (form.containsKey("resend")) {
            resend(context);
        } else {
            verify(context, form.getFirst("code"));
        }
    }

    private void start(AuthenticationFlowContext context, String input) {
        OtpIdentifier to = OtpIdentifier.parse(input).orElse(null);
        if (to == null) {
            context.challenge(identifyForm(context, "cmOtpInvalidIdentifier"));
            return;
        }
        AuthenticationSessionModel session = context.getAuthenticationSession();
        session.setAuthNote(NOTE_KIND, to.kind().name());
        session.setAuthNote(NOTE_VALUE, to.value());
        UserModel user = findUser(context, to);
        if (user != null) {
            session.setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, user.getUsername());
        }
        sendNewCode(context, to, 0, null);
    }

    private void resend(AuthenticationFlowContext context) {
        OtpIdentifier to = identifier(context.getAuthenticationSession());
        OtpChallenge challenge = OtpChallenge.readFrom(context.getAuthenticationSession()::getAuthNote);
        if (to == null || challenge == null) {
            authenticate(context);
            return;
        }
        if (!challenge.mayResend(Instant.now(), Duration.ofSeconds(RESEND_INTERVAL_SECONDS), MAX_SENDS)) {
            context.challenge(codeForm(context, to, "cmOtpResendNotYet", null));
            return;
        }
        sendNewCode(context, to, challenge.sends(), "cmOtpResent");
    }

    private void sendNewCode(AuthenticationFlowContext context, OtpIdentifier to, int previousSends, String info) {
        OtpChallenge.Issued issued = OtpChallenge.issue(Instant.now(), Duration.ofSeconds(intConfig(context, CFG_CODE_TTL_SECONDS, 300)), previousSends);
        switch (sender.send(config(context).getOrDefault(CFG_NTF_BASE_URL, DEFAULT_NTF_BASE_URL), to, issued.code())) {
            case SENT -> {
                issued.challenge().writeTo(context.getAuthenticationSession()::setAuthNote);
                context.challenge(codeForm(context, to, null, info));
            }
            case REJECTED -> context.challenge(identifyForm(context, "cmOtpInvalidIdentifier"));
            case FAILED -> {
                LOG.warnf("cm-ntf-svc could not send a sign-in code over %s", to.channel());
                context.challenge(identifyForm(context, "cmOtpSendFailed"));
            }
        }
    }

    private void verify(AuthenticationFlowContext context, String guess) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        OtpIdentifier to = identifier(session);
        OtpChallenge challenge = OtpChallenge.readFrom(session::getAuthNote);
        if (to == null || challenge == null) {
            authenticate(context);
            return;
        }
        UserModel user = findUser(context, to);
        if (user != null && isLockedOut(context, user)) {
            context.getEvent().user(user).error(Errors.USER_TEMPORARILY_DISABLED);
            context.failureChallenge(AuthenticationFlowError.USER_TEMPORARILY_DISABLED,
                    codeForm(context, to, "cmOtpLockedOut", null));
            return;
        }
        switch (challenge.verify(guess, Instant.now(), intConfig(context, CFG_MAX_ATTEMPTS, 5))) {
            case ACCEPTED -> succeed(context, to, user);
            case WRONG -> {
                challenge.withFailedAttempt().writeTo(session::setAuthNote);
                if (user != null) {
                    context.getEvent().user(user);
                }
                context.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        codeForm(context, to, "cmOtpWrongCode", null));
            }
            case EXPIRED -> context.challenge(codeForm(context, to, "cmOtpExpired", null));
            case TOO_MANY_ATTEMPTS -> {
                clear(session);
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        identifyForm(context, "cmOtpTooManyAttempts"));
            }
        }
    }

    private void succeed(AuthenticationFlowContext context, OtpIdentifier to, UserModel existing) {
        clear(context.getAuthenticationSession());
        UserModel user = existing != null ? existing : register(context, to);
        if (user == null) {
            context.failureChallenge(AuthenticationFlowError.INVALID_USER, identifyForm(context, "cmOtpNoAccount"));
            return;
        }
        if (!user.isEnabled()) {
            context.getEvent().user(user).error(Errors.USER_DISABLED);
            context.failureChallenge(AuthenticationFlowError.USER_DISABLED, identifyForm(context, "cmOtpAccountDisabled"));
            return;
        }
        if (to.kind() == OtpIdentifier.Kind.EMAIL && !user.isEmailVerified()) {
            user.setEmailVerified(true);
        }
        context.setUser(user);
        context.success();
    }

    private UserModel register(AuthenticationFlowContext context, OtpIdentifier to) {
        String roleName = config(context).getOrDefault(CFG_AUTO_REGISTER_ROLE, "DP");
        RealmModel realm = context.getRealm();
        RoleModel role = roleName.isBlank() ? null : realm.getRole(roleName);
        if (role == null) {
            return null;
        }
        UserModel user = context.getSession().users().addUser(realm, to.value());
        user.setEnabled(true);
        if (to.kind() == OtpIdentifier.Kind.EMAIL) {
            user.setEmail(to.value());
            user.setEmailVerified(true);
        } else {
            user.setSingleAttribute(PHONE_ATTRIBUTE, to.value());
        }
        user.grantRole(role);
        context.getEvent().user(user).detail("cm_otp_auto_registered", roleName);
        return user;
    }

    private static UserModel findUser(AuthenticationFlowContext context, OtpIdentifier to) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        if (to.kind() == OtpIdentifier.Kind.EMAIL) {
            UserModel byEmail = session.users().getUserByEmail(realm, to.value());
            return byEmail != null ? byEmail : session.users().getUserByUsername(realm, to.value());
        }
        return session.users().searchForUserByUserAttributeStream(realm, PHONE_ATTRIBUTE, to.value())
                .findFirst()
                .orElseGet(() -> session.users().getUserByUsername(realm, to.value()));
    }

    private static boolean isLockedOut(AuthenticationFlowContext context, UserModel user) {
        RealmModel realm = context.getRealm();
        if (!realm.isBruteForceProtected()) {
            return false;
        }
        BruteForceProtector protector = context.getSession().getProvider(BruteForceProtector.class);
        return protector.isTemporarilyDisabled(context.getSession(), realm, user)
                || protector.isPermanentlyLockedOut(context.getSession(), realm, user);
    }

    private static OtpIdentifier identifier(AuthenticationSessionModel session) {
        String kind = session.getAuthNote(NOTE_KIND);
        String value = session.getAuthNote(NOTE_VALUE);
        return kind == null || value == null ? null : new OtpIdentifier(OtpIdentifier.Kind.valueOf(kind), value);
    }

    private static void clear(AuthenticationSessionModel session) {
        for (String note : OtpChallenge.noteNames()) {
            session.removeAuthNote(note);
        }
        session.removeAuthNote(NOTE_KIND);
        session.removeAuthNote(NOTE_VALUE);
    }

    private static Response identifyForm(AuthenticationFlowContext context, String error) {
        var form = context.form();
        if (error != null) {
            form.setError(error);
        }
        return form.createForm(IDENTIFY_FORM);
    }

    private static Response codeForm(AuthenticationFlowContext context, OtpIdentifier to, String error, String info) {
        var form = context.form()
                .setAttribute("cmOtpDestination", to.masked())
                .setAttribute("cmOtpCodeLength", OtpChallenge.CODE_LENGTH)
                .setAttribute("cmOtpValidityMinutes", Math.max(1, intConfig(context, CFG_CODE_TTL_SECONDS, 300) / 60));
        if (error != null) {
            form.setError(error);
        } else if (info != null) {
            form.setInfo(info);
        }
        return form.createForm(CODE_FORM);
    }

    private static Map<String, String> config(AuthenticationFlowContext context) {
        AuthenticatorConfigModel model = context.getAuthenticatorConfig();
        return model == null || model.getConfig() == null ? Map.of() : model.getConfig();
    }

    private static int intConfig(AuthenticationFlowContext context, String key, int fallback) {
        try {
            return Integer.parseInt(config(context).getOrDefault(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
