<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("cmOtpIdentifyTitle")}
    <#elseif section = "form">
        <form id="cm-otp-identify-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="identifier" class="${properties.kcLabelClass!}">${msg("cmOtpIdentifierLabel")}</label>
                <input id="identifier" name="identifier" type="text" class="${properties.kcInputClass!}"
                       autocomplete="username" inputmode="email" autofocus required />
                <div class="${properties.kcInputHelperTextBeforeClass!}" id="identifier-help">${msg("cmOtpIdentifierHelp")}</div>
            </div>
            <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                <input id="cm-otp-send" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                       type="submit" value="${msg("cmOtpSendCode")}" />
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
