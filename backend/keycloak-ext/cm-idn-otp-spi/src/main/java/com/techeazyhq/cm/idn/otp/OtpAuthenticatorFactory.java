package com.techeazyhq.cm.idn.otp;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/** Registers {@link OtpAuthenticator} as the "CM email/SMS one-time code" step, configurable per flow execution. */
public class OtpAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "cm-otp-authenticator";

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENTS = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    private NtfOtpSender sender;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "CM email/SMS one-time code";
    }

    @Override
    public String getHelpText() {
        return "Passwordless sign-in: sends a 6-digit code to the email address or phone number entered, through cm-ntf-svc.";
    }

    @Override
    public String getReferenceCategory() {
        return "otp";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENTS;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of(
                property(OtpAuthenticator.CFG_NTF_BASE_URL, "cm-ntf-svc URL",
                        "Base URL of cm-ntf-svc, which delivers the code", OtpAuthenticator.DEFAULT_NTF_BASE_URL),
                property(OtpAuthenticator.CFG_CODE_TTL_SECONDS, "Code validity (seconds)",
                        "How long a code can be used", "300"),
                property(OtpAuthenticator.CFG_MAX_ATTEMPTS, "Wrong guesses per code",
                        "After this many wrong guesses the person must start again", "5"),
                property(OtpAuthenticator.CFG_AUTO_REGISTER_ROLE, "Role for new accounts",
                        "Realm role given to an account created when an unknown address proves itself; empty allows existing accounts only",
                        "DP"));
    }

    private static ProviderConfigProperty property(String name, String label, String help, String defaultValue) {
        ProviderConfigProperty property = new ProviderConfigProperty(name, label, help, ProviderConfigProperty.STRING_TYPE, defaultValue);
        property.setRequired(false);
        return property;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new OtpAuthenticator(sender);
    }

    @Override
    public void init(Config.Scope config) {
        sender = new NtfOtpSender(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build(), Duration.ofSeconds(10));
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
