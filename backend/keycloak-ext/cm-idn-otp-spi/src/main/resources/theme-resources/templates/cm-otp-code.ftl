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
