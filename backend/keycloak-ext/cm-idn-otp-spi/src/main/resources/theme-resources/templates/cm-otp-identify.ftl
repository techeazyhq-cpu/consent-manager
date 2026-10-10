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
