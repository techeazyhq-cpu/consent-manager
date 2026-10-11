<#--
  Copyright 2026 Vasantha Kumar

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

  @author Vasantha Kumar <vasantha.kumar@hotmail.com>
-->
<#--
  Consent Manager's page layout for every login page, its own and those inherited from Keycloak's base theme. It keeps
  base's registrationLayout contract (the macro parameters and the header, show-username, form, socialProviders and
  info sections) so those pages render unchanged inside it. The audience comes from the theme's cmAudience property.
-->
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="<#if realm.internationalizationEnabled>${locale.currentLanguageTag}<#else>en</#if>">
<head>
    <meta charset="utf-8">
    <meta name="robots" content="noindex, nofollow">
    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("cmPageTitle", msg("cmAudienceName"))}</title>
    <link rel="icon" type="image/svg+xml" href="${url.resourcesPath}/img/cm-mark.svg">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet">
        </#list>
    </#if>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <#if realm.internationalizationEnabled && locale.supported?size gt 1>
        <script src="${url.resourcesPath}/js/menu-button-links.js" type="module"></script>
    </#if>
    <#-- Base's check for a sign-in completed in another tab: it restarts this one so SSO picks the session up. -->
    <script type="module">
        import { checkCookiesAndSetTimer } from "${url.resourcesPath}/js/authChecker.js";
        checkCookiesAndSetTimer("${url.ssoLoginInOtherTabsUrl?no_esc}");
    </script>
</head>

<body class="cm-login ${bodyClass}" data-audience="${properties.cmAudience!'cm'}">
<main class="cm-shell">
    <header class="cm-brand">
        <svg class="cm-brand-mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
            <path d="M16 2.5 4.5 7v8.2c0 7 4.9 12.6 11.5 14.3 6.6-1.7 11.5-7.3 11.5-14.3V7z"/>
            <path class="cm-brand-check" d="m10.5 16 4 4 7-8"/>
        </svg>
        <div>
            <p class="cm-brand-name">${msg("cmProductName")}</p>
            <p class="cm-brand-audience">${msg("cmAudienceName")}</p>
        </div>
    </header>

    <section class="cm-card" aria-labelledby="kc-page-title">
        <#if realm.internationalizationEnabled && locale.supported?size gt 1>
            <div id="kc-locale" class="cm-locale">
                <div id="kc-locale-dropdown" class="menu-button-links">
                    <button id="kc-current-locale-link" aria-label="${msg("languages")}" aria-haspopup="true"
                            aria-expanded="false" aria-controls="language-switch1">${locale.current}</button>
                    <ul role="menu" tabindex="-1" aria-labelledby="kc-current-locale-link" aria-activedescendant=""
                        id="language-switch1">
                        <#list locale.supported as supportedLocale>
                            <li role="none">
                                <a role="menuitem" id="language-${supportedLocale?counter}"
                                   href="${supportedLocale.url}">${supportedLocale.label}</a>
                            </li>
                        </#list>
                    </ul>
                </div>
            </div>
        </#if>

        <p class="cm-tagline">${msg("cmAudienceTagline")}</p>

        <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
            <#nested "show-username">
            <div id="kc-username" class="cm-username">
                <span id="kc-attempted-username">${auth.attemptedUsername}</span>
                <a id="reset-login" href="${url.loginRestartFlowUrl}">${msg("cmRestartSignIn")}</a>
            </div>
        <#else>
            <h1 id="kc-page-title"><#nested "header"></h1>
        </#if>
        <#-- Rendered ahead of its place so the audience intro shows only above the first sign-in step
             (cm-idn-otp-spi's identify form), not on the code, error, info or logout pages. -->
        <#assign formSection><#nested "form"></#assign>
        <#if formSection?markup_string?contains('id="cm-otp-identify-form"')>
            <p class="cm-intro">${msg("cmAudienceIntro")}</p>
        </#if>
        <#if displayRequiredFields>
            <p class="cm-required-note"><span class="required">*</span> ${msg("requiredFields")}</p>
        </#if>

        <#-- App-initiated actions should not see warnings about the action they are completing. -->
        <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="${properties.kcAlertClass!} cm-alert-${message.type}"
                 role="<#if message.type = 'error'>alert<#else>status</#if>">
                <span class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        ${formSection}

        <#if auth?has_content && auth.showTryAnotherWayLink()>
            <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                <input type="hidden" name="tryAnotherWay" value="on"/>
                <button type="submit" class="cm-link-button">${msg("doTryAnotherWay")}</button>
            </form>
        </#if>

        <#nested "socialProviders">

        <#if displayInfo>
            <div id="kc-info" class="cm-info">
                <#nested "info">
            </div>
        </#if>
    </section>

    <footer class="cm-footer">${msg("cmFooterNote")}</footer>
</main>
</body>
</html>
</#macro>
