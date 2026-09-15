package org.joget.marketplace;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import org.joget.apps.form.model.FormRowSet;

public class smsNotificationFormElement extends Element implements FormBuilderPaletteElement, PluginWebSupport, PropertyEditable {

    @Override
    public String getName() {
        return "Phone Number Verification Tool";
    }

    @Override
    public String getVersion() {
        return "1.4";
    }

    @Override
    public String getDescription() {
        return "A form field for phone number input with OTP SMS verification prior to form submission.";
    }

    @Override
    public String getLabel() {
        return "Phone Number Verification";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/smsNotificationFormElement.json", null, true, "message/smsNotificationFormElement");
    }

    @Override
    public String getFormBuilderCategory() {
        return "Custom";
    }

    @Override
    public int getFormBuilderPosition() {
        return 100;
    }

    @Override
    public String getFormBuilderIcon() {
        return "<i class=\"fas fa-mobile-alt\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<div class=\"form-group\"><label class=\"label\">Phone Number Verification</label><input type=\"text\" class=\"form-control\" placeholder=\"Enter Phone Number\" disabled/></div>";
    }

    @Override
    public FormRowSet formatData(FormData formData) {
        FormRowSet rowSet = super.formatData(formData);
        String elementId = getPropertyString("id");
        if (elementId != null && formData != null) {
            String phone = formData.getRequestParameter(elementId);
            HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
            if (request != null) {
                HttpSession session = request.getSession(false);
                Object verifiedObj = session != null ? session.getAttribute("OTP_VERIFIED_" + elementId) : null;
                Object verifiedPhoneObj = session != null ? session.getAttribute("OTP_VERIFIED_PHONE_" + elementId) : null;

                boolean isVerified = Boolean.TRUE.equals(verifiedObj) 
                        && phone != null 
                        && !phone.trim().isEmpty() 
                        && phone.trim().equalsIgnoreCase(String.valueOf(verifiedPhoneObj));

                if (!isVerified) {
                    String errorMessage = getPropertyString("errorMessage");
                    if (errorMessage == null || errorMessage.trim().isEmpty()) {
                        errorMessage = "Security Error: Phone number must be verified using OTP before submitting the form.";
                    }
                    formData.addFormError(elementId, errorMessage);
                }
            }
        }
        return rowSet;
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String elementId = getPropertyString("id");
        if (elementId == null || elementId.trim().isEmpty()) {
            elementId = "phone_verify_" + System.currentTimeMillis();
        }
        String label = getPropertyString("label");
        if (label == null || label.trim().isEmpty()) {
            label = "Phone Number";
        }
        String placeholder = getPropertyString("placeholder");
        if (placeholder == null || placeholder.trim().isEmpty()) {
            placeholder = "Enter phone number (e.g. +1234567890)";
        }
        String sendOtpButtonLabel = getPropertyString("sendOtpButtonLabel");
        if (sendOtpButtonLabel == null || sendOtpButtonLabel.trim().isEmpty()) {
            sendOtpButtonLabel = "Send OTP";
        }
        String verifyOtpButtonLabel = getPropertyString("verifyOtpButtonLabel");
        if (verifyOtpButtonLabel == null || verifyOtpButtonLabel.trim().isEmpty()) {
            verifyOtpButtonLabel = "Verify OTP";
        }
        String buttonClass = getPropertyString("buttonClass");
        if (buttonClass == null || buttonClass.trim().isEmpty()) {
            buttonClass = "btn btn-primary";
        }
        String successMessage = getPropertyString("successMessage");
        if (successMessage == null || successMessage.trim().isEmpty()) {
            successMessage = "Phone number verified successfully!";
        }
        String errorMessage = getPropertyString("errorMessage");
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = "Invalid or expired OTP code. Please try again.";
        }

        String existingValue = FormUtil.getElementPropertyValue(this, formData);
        if (existingValue == null) {
            existingValue = "";
        }

        // Check if already verified in session
        boolean isAlreadyVerified = false;
        HttpServletRequest req = WorkflowUtil.getHttpServletRequest();
        if (req != null) {
            HttpSession sess = req.getSession(false);
            Object verifiedObj = sess != null ? sess.getAttribute("OTP_VERIFIED_" + elementId) : null;
            Object verifiedPhoneObj = sess != null ? sess.getAttribute("OTP_VERIFIED_PHONE_" + elementId) : null;
            if (Boolean.TRUE.equals(verifiedObj) && !existingValue.trim().isEmpty() && existingValue.trim().equalsIgnoreCase(String.valueOf(verifiedPhoneObj))) {
                isAlreadyVerified = true;
            }
        }

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef != null ? appDef.getId() : "";
        String appVersion = appDef != null ? appDef.getVersion().toString() : "";
        String formDefId = "";
        
        Element root = this;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        if (root instanceof Form) {
            formDefId = ((Form) root).getPropertyString("id");
        }

        String pluginName = getClassName();
        String url = "/jw/web/json/app/" + appId + "/" + appVersion + "/plugin/" + pluginName + "/service?formDefId=" + formDefId + "&elementId=" + elementId;

        StringBuilder sb = new StringBuilder();
        sb.append("<style type=\"text/css\">\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" { background:#ffffff; border:1px solid #e3e6f0; border-radius:8px; padding:18px; margin:15px 0; font-family:inherit; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" .form-group { margin-bottom:12px; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" label.phone-label { display:block; margin-bottom:6px; font-weight:600; font-size:14px; color:#2e384d; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" .input-group-custom { display:flex; gap:8px; align-items:center; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" input.form-control { flex:1; padding:8px 12px; font-size:14px; border:1px solid #d1d3e2; border-radius:6px; background-color:#fff; color:#495057; transition: border-color .15s ease-in-out,box-shadow .15s ease-in-out; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" input.form-control:focus { border-color:#4e73df; outline:0; box-shadow:0 0 0 0.2rem rgba(78,115,223,0.25); }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" .btn { white-space:nowrap; font-weight:600; padding:8px 16px; font-size:14px; border-radius:6px; cursor:pointer; transition: all .15s ease-in-out; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" .alert { padding:10px 14px; border-radius:6px; font-size:13px; margin-top:10px; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" .alert-success { background-color:#d4edda; color:#155724; border:1px solid #c3e6cb; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" .alert-danger { background-color:#f8d7da; color:#721c24; border:1px solid #f5c6cb; }\n");
        sb.append("#phone_verify_widget_").append(elementId).append(" .verified-badge { display:inline-flex; align-items:center; gap:6px; color:#1cc88a; font-weight:600; font-size:14px; background:#e8fadf; padding:6px 12px; border-radius:6px; border:1px solid #b7f4cf; }\n");
        sb.append(".otp-locked-section { opacity:0.5; pointer-events:none !important; user-select:none !important; filter:blur(2.5px); transition: all 0.3s ease; }\n");
        sb.append(".otp-locked-banner { background:#fff3cd; border:1px solid #ffeeba; color:#856404; padding:10px 14px; border-radius:6px; font-weight:600; font-size:13px; margin:10px 0; display:flex; align-items:center; gap:8px; }\n");
        sb.append("</style>\n");

        sb.append("<div id=\"phone_verify_widget_").append(elementId).append("\" class=\"phone-verification-widget form-cell-element form-cell\">\n");
        sb.append("  <label class=\"phone-label\" for=\"").append(elementId).append("\">").append(escapeHtml(label)).append("</label>\n");

        sb.append("  <div class=\"input-group-custom\">\n");
        sb.append("    <input type=\"tel\" id=\"").append(elementId).append("\" name=\"").append(elementId).append("\" class=\"form-control\" value=\"").append(escapeHtml(existingValue)).append("\" placeholder=\"").append(escapeHtml(placeholder)).append("\" ").append(isAlreadyVerified ? "readonly" : "").append(" />\n");
        sb.append("    <button type=\"button\" id=\"").append(elementId).append("_send_btn\" class=\"").append(escapeHtml(buttonClass)).append("\" style=\"").append(isAlreadyVerified ? "display:none;" : "").append("\"><i class=\"fas fa-paper-plane mr-1\"></i> ").append(escapeHtml(sendOtpButtonLabel)).append("</button>\n");
        sb.append("    <span id=\"").append(elementId).append("_badge\" class=\"verified-badge\" style=\"").append(isAlreadyVerified ? "" : "display:none;").append("\"><i class=\"fas fa-check-circle\"></i> Verified</span>\n");
        sb.append("  </div>\n");

        sb.append("  <div id=\"").append(elementId).append("_lock_banner\" class=\"otp-locked-banner\" style=\"").append(isAlreadyVerified ? "display:none;" : "").append("\"><i class=\"fas fa-lock\"></i> <span>Form Locked: Please verify your phone number using OTP to unlock the rest of the form.</span></div>\n");

        // OTP Section
        sb.append("  <div id=\"").append(elementId).append("_otp_container\" style=\"display:none; margin-top:12px;\">\n");
        sb.append("    <label style=\"font-size:13px; font-weight:600; color:#5a5c69; margin-bottom:4px; display:block;\">Enter 6-Digit OTP Code Sent to Your Phone:</label>\n");
        sb.append("    <div class=\"input-group-custom\">\n");
        sb.append("      <input type=\"text\" id=\"").append(elementId).append("_otp_input\" class=\"form-control\" placeholder=\"e.g. 123456\" maxlength=\"10\" autocomplete=\"off\" />\n");
        sb.append("      <button type=\"button\" id=\"").append(elementId).append("_verify_btn\" class=\"btn btn-success\"><i class=\"fas fa-user-check mr-1\"></i> ").append(escapeHtml(verifyOtpButtonLabel)).append("</button>\n");
        sb.append("      <button type=\"button\" id=\"").append(elementId).append("_resend_btn\" class=\"btn btn-outline-secondary\" style=\"margin-left:4px;\"><i class=\"fas fa-redo mr-1\"></i> Resend OTP</button>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        sb.append("  <div id=\"").append(elementId).append("_alert\" class=\"alert\" style=\"display:none;\"></div>\n");
        sb.append("</div>\n");

        // Client JavaScript
        sb.append("<script type=\"text/javascript\">\n");
        sb.append("$(document).ready(function(){\n");
        sb.append("  var widget = $('#phone_verify_widget_").append(elementId).append("');\n");
        sb.append("  var phoneInput = $('#").append(elementId).append("');\n");
        sb.append("  var sendBtn = $('#").append(elementId).append("_send_btn');\n");
        sb.append("  var verifyBtn = $('#").append(elementId).append("_verify_btn');\n");
        sb.append("  var resendBtn = $('#").append(elementId).append("_resend_btn');\n");
        sb.append("  var otpContainer = $('#").append(elementId).append("_otp_container');\n");
        sb.append("  var otpInput = $('#").append(elementId).append("_otp_input');\n");
        sb.append("  var alertBox = $('#").append(elementId).append("_alert');\n");
        sb.append("  var lockBanner = $('#").append(elementId).append("_lock_banner');\n");
        sb.append("  var verifiedBadge = $('#").append(elementId).append("_badge');\n");
        sb.append("  var serviceUrl = '").append(url).append("';\n");
        sb.append("  var isVerifiedState = ").append(isAlreadyVerified ? "true" : "false").append(";\n");

        // Lock / Unlock helper functions
        sb.append("  function isFormBuilderMode() {\n");
        sb.append("    return ($('.form-builder-canvas, .form-builder, body.builder, #form-builder-canvas, .builder-palette').length > 0 || window.location.href.indexOf('/form/builder/') !== -1);\n");
        sb.append("  }\n");

        sb.append("  function lockRestOfForm() {\n");
        sb.append("    if (isVerifiedState || isFormBuilderMode()) return;\n");
        sb.append("    var parentForm = widget.closest('form');\n");
        sb.append("    if (parentForm.length) {\n");
        sb.append("      var otherCells = parentForm.find('.form-cell').filter(function(){\n");
        sb.append("        return $(this).attr('id') !== widget.attr('id') && $(this).find(widget).length === 0;\n");
        sb.append("      });\n");
        sb.append("      otherCells.addClass('otp-locked-section');\n");
        sb.append("      otherCells.find('input, select, textarea, button, a.btn').prop('disabled', true).addClass('otp-disabled-input');\n");
        sb.append("      parentForm.find('.form-section, .form-column, .subform-container').filter(function(){\n");
        sb.append("        return $(this).find(widget).length === 0;\n");
        sb.append("      }).addClass('otp-locked-section');\n");
        sb.append("    }\n");
        sb.append("  }\n");

        sb.append("  function unlockRestOfForm() {\n");
        sb.append("    isVerifiedState = true;\n");
        sb.append("    if (isFormBuilderMode()) return;\n");
        sb.append("    var parentForm = widget.closest('form');\n");
        sb.append("    if (parentForm.length) {\n");
        sb.append("      parentForm.find('.otp-locked-section').removeClass('otp-locked-section');\n");
        sb.append("      parentForm.find('.otp-disabled-input').prop('disabled', false).removeClass('otp-disabled-input');\n");
        sb.append("    }\n");
        sb.append("    lockBanner.slideUp();\n");
        sb.append("  }\n");

        // Initial lock application if not verified and not in Form Builder
        sb.append("  if (!isFormBuilderMode()) {\n");
        sb.append("    setTimeout(lockRestOfForm, 100);\n");
        sb.append("  } else {\n");
        sb.append("    lockBanner.hide();\n");
        sb.append("  }\n");

        // If phone input is changed after verification, reset verified state
        sb.append("  phoneInput.on('input change', function(){\n");
        sb.append("    if (isVerifiedState) {\n");
        sb.append("      isVerifiedState = false;\n");
        sb.append("      verifiedBadge.hide();\n");
        sb.append("      sendBtn.show();\n");
        sb.append("      phoneInput.prop('readonly', false);\n");
        sb.append("      alertBox.hide();\n");
        sb.append("      lockBanner.slideDown();\n");
        sb.append("      lockRestOfForm();\n");
        sb.append("    }\n");
        sb.append("  });\n");

        // Send OTP action
        sb.append("  function triggerSendOtp(btn) {\n");
        sb.append("    var phoneVal = $.trim(phoneInput.val());\n");
        sb.append("    if (!phoneVal) {\n");
        sb.append("      alertBox.removeClass('alert-success').addClass('alert-danger').html('<strong>Error:</strong> Please enter a phone number first.').slideDown();\n");
        sb.append("      return;\n");
        sb.append("    }\n");
        sb.append("    var origHtml = btn.html();\n");
        sb.append("    btn.html('<i class=\"fas fa-spinner fa-spin mr-1\"></i> Sending...').prop('disabled', true);\n");
        sb.append("    alertBox.hide().removeClass('alert-success alert-danger').text('');\n");
        sb.append("    $.ajax({\n");
        sb.append("      url: serviceUrl,\n");
        sb.append("      type: 'POST',\n");
        sb.append("      data: { action: 'sendOtp', phoneNumber: phoneVal },\n");
        sb.append("      success: function(resp){\n");
        sb.append("        btn.html(origHtml).prop('disabled', false);\n");
        sb.append("        if (resp && resp.success) {\n");
        sb.append("          alertBox.addClass('alert-success').html('<strong>Success:</strong> ' + (resp.message || 'OTP sent successfully!')).slideDown();\n");
        sb.append("          otpContainer.slideDown();\n");
        sb.append("          otpInput.focus();\n");
        sb.append("        } else {\n");
        sb.append("          var err = (resp && resp.message) ? resp.message : 'Failed to send OTP.';\n");
        sb.append("          alertBox.addClass('alert-danger').html('<strong>Error:</strong> ' + err).slideDown();\n");
        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      error: function(xhr, status, error){\n");
        sb.append("        btn.html(origHtml).prop('disabled', false);\n");
        sb.append("        var detail = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : error;\n");
        sb.append("        alertBox.addClass('alert-danger').html('<strong>Request Failed:</strong> ' + detail).slideDown();\n");
        sb.append("      }\n");
        sb.append("    });\n");
        sb.append("  }\n");

        sb.append("  sendBtn.click(function(e){ e.preventDefault(); triggerSendOtp($(this)); });\n");
        sb.append("  resendBtn.click(function(e){ e.preventDefault(); triggerSendOtp($(this)); });\n");

        // Verify OTP action
        sb.append("  verifyBtn.click(function(e){\n");
        sb.append("    e.preventDefault();\n");
        sb.append("    var phoneVal = $.trim(phoneInput.val());\n");
        sb.append("    var otpVal = $.trim(otpInput.val());\n");
        sb.append("    if (!otpVal) {\n");
        sb.append("      alertBox.removeClass('alert-success').addClass('alert-danger').html('<strong>Error:</strong> Please enter the OTP code.').slideDown();\n");
        sb.append("      return;\n");
        sb.append("    }\n");
        sb.append("    var origHtml = verifyBtn.html();\n");
        sb.append("    verifyBtn.html('<i class=\"fas fa-spinner fa-spin mr-1\"></i> Verifying...').prop('disabled', true);\n");
        sb.append("    alertBox.hide().removeClass('alert-success alert-danger').text('');\n");
        sb.append("    $.ajax({\n");
        sb.append("      url: serviceUrl,\n");
        sb.append("      type: 'POST',\n");
        sb.append("      data: { action: 'verifyOtp', phoneNumber: phoneVal, otpCode: otpVal },\n");
        sb.append("      success: function(resp){\n");
        sb.append("        verifyBtn.html(origHtml).prop('disabled', false);\n");
        sb.append("        if (resp && resp.verified) {\n");
        sb.append("          alertBox.addClass('alert-success').html('<strong>Verified:</strong> ' + (resp.message || '").append(escapeJson(successMessage)).append("')).slideDown();\n");
        sb.append("          otpContainer.slideUp();\n");
        sb.append("          sendBtn.hide();\n");
        sb.append("          verifiedBadge.show();\n");
        sb.append("          phoneInput.prop('readonly', true);\n");
        sb.append("          unlockRestOfForm();\n");
        sb.append("        } else {\n");
        sb.append("          var err = (resp && resp.message) ? resp.message : '").append(escapeJson(errorMessage)).append("';\n");
        sb.append("          alertBox.addClass('alert-danger').html('<strong>Verification Failed:</strong> ' + err).slideDown();\n");
        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      error: function(xhr, status, error){\n");
        sb.append("        verifyBtn.html(origHtml).prop('disabled', false);\n");
        sb.append("        var detail = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : error;\n");
        sb.append("        alertBox.addClass('alert-danger').html('<strong>Request Failed:</strong> ' + detail).slideDown();\n");
        sb.append("      }\n");
        sb.append("    });\n");
        sb.append("  });\n");

        // Prevent parent form submit if phone number entered but not verified
        sb.append("  phoneInput.closest('form').on('submit', function(e){\n");
        sb.append("    if (!isVerifiedState) {\n");
        sb.append("      e.preventDefault();\n");
        sb.append("      e.stopPropagation();\n");
        sb.append("      alertBox.removeClass('alert-success').addClass('alert-danger').html('<strong>Verification Required:</strong> Please enter your phone number and complete OTP verification before submitting the form.').slideDown();\n");
        sb.append("      $('html, body').animate({ scrollTop: widget.offset().top - 100 }, 300);\n");
        sb.append("      return false;\n");
        sb.append("    }\n");
        sb.append("  });\n");

        sb.append("});\n");
        sb.append("</script>\n");

        return sb.toString();
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            String action = request.getParameter("action");
            if (action == null) action = "sendOtp";

            String elementId = request.getParameter("elementId");
            if (elementId == null) elementId = getPropertyString("id");

            Map<String, Object> mergedProperties = new HashMap<>();

            String appId = request.getParameter("appId");
            String appVersion = request.getParameter("appVersion");
            String formDefId = request.getParameter("formDefId");

            AppDefinition appDef = null;
            if (appId != null && !appId.isEmpty()) {
                org.joget.apps.app.service.AppService appService = (org.joget.apps.app.service.AppService) AppUtil.getApplicationContext().getBean("appService");
                appDef = appService.getAppDefinition(appId, appVersion);
            }
            if (appDef == null) {
                appDef = AppUtil.getCurrentAppDefinition();
            }

            if (appDef != null && formDefId != null && elementId != null) {
                try {
                    ApplicationContext ac = AppUtil.getApplicationContext();
                    FormDefinitionDao formDefinitionDao = (FormDefinitionDao) ac.getBean("formDefinitionDao");
                    FormService formService = (FormService) ac.getBean("formService");
                    FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
                    if (formDef != null) {
                        Form form = (Form) formService.createElementFromJson(formDef.getJson());
                        Element element = FormUtil.findElement(elementId, form, null);
                        if (element != null && element.getProperties() != null) {
                            Map elemProps = element.getProperties();
                            for (Object k : elemProps.keySet()) {
                                mergedProperties.put(k.toString(), elemProps.get(k));
                            }
                        }
                    }
                } catch (Exception ex) {
                    LogUtil.warn(getClassName(), "Could not load saved form element properties: " + ex.getMessage());
                }
            }

            try {
                mergedProperties = AppPluginUtil.getDefaultProperties(this, mergedProperties, appDef, null);
            } catch (Exception ex) {
                LogUtil.warn(getClassName(), "Could not merge global plugin default properties: " + ex.getMessage());
            }

            if ("verifyOtp".equalsIgnoreCase(action)) {
                String phoneNumber = request.getParameter("phoneNumber");
                String otpCode = request.getParameter("otpCode");

                if (phoneNumber == null || phoneNumber.trim().isEmpty() || otpCode == null || otpCode.trim().isEmpty()) {
                    response.setStatus(400);
                    response.getWriter().write("{\"status\":400, \"success\":false, \"verified\":false, \"message\":\"Phone number and OTP code are required.\"}");
                    return;
                }

                HttpSession session = request.getSession(false);
                String storedOtp = session != null ? (String) session.getAttribute("OTP_CODE_" + elementId) : null;
                String storedPhone = session != null ? (String) session.getAttribute("OTP_PHONE_" + elementId) : null;
                Long storedTime = session != null ? (Long) session.getAttribute("OTP_TIME_" + elementId) : null;

                boolean verified = false;
                if (storedOtp != null && storedPhone != null && storedTime != null) {
                    long elapsed = System.currentTimeMillis() - storedTime;
                    if (storedOtp.equals(otpCode.trim()) && storedPhone.equalsIgnoreCase(phoneNumber.trim()) && elapsed <= 10 * 60 * 1000) {
                        verified = true;
                    }
                }

                if (verified) {
                    if (session == null) session = request.getSession(true);
                    session.setAttribute("OTP_VERIFIED_" + elementId, Boolean.TRUE);
                    session.setAttribute("OTP_VERIFIED_PHONE_" + elementId, phoneNumber.trim());

                    String successMsg = (String) mergedProperties.get("successMessage");
                    if (successMsg == null || successMsg.trim().isEmpty()) successMsg = "Phone number verified successfully!";
                    response.setStatus(200);
                    response.getWriter().write("{\"status\":200, \"success\":true, \"verified\":true, \"message\":\"" + escapeJson(successMsg) + "\"}");
                } else {
                    String errorMsg = (String) mergedProperties.get("errorMessage");
                    if (errorMsg == null || errorMsg.trim().isEmpty()) errorMsg = "Invalid or expired OTP code. Please try again.";
                    response.setStatus(400);
                    response.getWriter().write("{\"status\":400, \"success\":false, \"verified\":false, \"message\":\"" + escapeJson(errorMsg) + "\"}");
                }
            } else {
                // Action: sendOtp
                String phoneNumber = request.getParameter("phoneNumber");
                if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                    response.setStatus(400);
                    response.getWriter().write("{\"status\":400, \"success\":false, \"message\":\"Please enter a valid phone number.\"}");
                    return;
                }

                int otpInt = 100000 + new SecureRandom().nextInt(900000);
                String otpCode = String.valueOf(otpInt);

                HttpSession session = request.getSession(true);
                session.setAttribute("OTP_CODE_" + elementId, otpCode);
                session.setAttribute("OTP_PHONE_" + elementId, phoneNumber.trim());
                session.setAttribute("OTP_TIME_" + elementId, System.currentTimeMillis());
                session.setAttribute("OTP_VERIFIED_" + elementId, Boolean.FALSE);

                String template = (String) mergedProperties.get("otpMessageTemplate");
                if (template == null || template.trim().isEmpty()) {
                    template = "Your verification code is: {otp}";
                }
                String messageText = template.replace("{otp}", otpCode);

                phoneNumber = phoneNumber.trim();
                mergedProperties.put("to", phoneNumber);
                mergedProperties.put("twilioTo", phoneNumber);
                mergedProperties.put("nexmoTo", phoneNumber);
                mergedProperties.put("infobipTo", phoneNumber);
                mergedProperties.put("plivoTo", phoneNumber);
                mergedProperties.put("brevoRecipient", phoneNumber);
                mergedProperties.put("msg91To", phoneNumber);

                mergedProperties.put("message", messageText);
                mergedProperties.put("twilioBody", messageText);
                mergedProperties.put("nexmoText", messageText);
                mergedProperties.put("infobipText", messageText);
                mergedProperties.put("plivoText", messageText);
                mergedProperties.put("brevoContent", messageText);
                mergedProperties.put("msg91Body", messageText);

                smsNotificationTool tool = new smsNotificationTool();
                Map<String, Object> apiResult = tool.performApiCallAndAuditLog(mergedProperties, null, appDef);

                int statusCode = 200;
                if (apiResult != null && apiResult.get("status") != null) {
                    try {
                        statusCode = Integer.parseInt(apiResult.get("status").toString());
                    } catch (Exception ignored) {}
                }

                if (statusCode >= 200 && statusCode < 300) {
                    response.setStatus(200);
                    response.getWriter().write("{\"status\":200, \"success\":true, \"message\":\"OTP sent successfully to " + escapeJson(phoneNumber) + "\"}");
                } else {
                    String errStr = apiResult != null ? (String) apiResult.get("response") : "Failed to send OTP.";
                    response.setStatus(statusCode >= 400 && statusCode < 600 ? statusCode : 500);
                    response.getWriter().write("{\"status\":" + statusCode + ", \"success\":false, \"message\":\"Failed to send OTP: " + escapeJson(errStr) + "\"}");
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error in Phone Number Verification Form Element webservice");
            response.setStatus(500);
            response.getWriter().write("{\"status\":500, \"success\":false, \"message\":\"Internal Server Error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
