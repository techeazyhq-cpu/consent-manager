<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("cmOtpCodeTitle")}
    <#elseif section = "form">
        <p id="cm-otp-sent-to">${msg("cmOtpSentTo", cmOtpDestination, cmOtpValidityMinutes?c)}</p>
        <form id="cm-otp-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="code" class="${properties.kcLabelClass!}">${msg("cmOtpCodeLabel")}</label>
                <input id="code" name="code" type="text" class="${properties.kcInputClass!}" autocomplete="one-time-code"
                       inputmode="numeric" pattern="[0-9 ]*" maxlength="${cmOtpCodeLength + 2}" autofocus required />
            </div>
            <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                <input id="cm-otp-verify" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                       type="submit" value="${msg("cmOtpVerify")}" />
            </div>
        </form>
        <form id="cm-otp-other-actions" action="${url.loginAction}" method="post" style="display:flex;gap:16px;justify-content:center">
            <button type="submit" name="resend" value="true" class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!}">${msg("cmOtpResend")}</button>
            <button type="submit" name="restart" value="true" class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!}">${msg("cmOtpUseAnother")}</button>
        </form>
    </#if>
</@layout.registrationLayout>
