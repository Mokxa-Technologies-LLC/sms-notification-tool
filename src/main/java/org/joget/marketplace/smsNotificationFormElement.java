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
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public class smsNotificationFormElement extends Element implements FormBuilderPaletteElement, PluginWebSupport, PropertyEditable {

    @Override
    public String getName() {
        return "SMS Form Button";
    }

    @Override
    public String getVersion() {
        return "1.4";
    }

    @Override
    public String getDescription() {
        return "A button that triggers an SMS or API call directly from the form without submitting it.";
    }

    @Override
    public String getLabel() {
        return "SMS Button";
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
        return "<i class=\"fas fa-sms\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<button class=\"btn btn-primary\">SMS Button</button>";
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String elementId = getPropertyString("id");
        if (elementId == null || elementId.trim().isEmpty()) {
            elementId = "sms_btn_" + System.currentTimeMillis();
        }
        String buttonLabel = getPropertyString("buttonLabel");
        if (buttonLabel == null || buttonLabel.trim().isEmpty()) {
            buttonLabel = "Send SMS / Execute API";
        }
        String buttonClass = getPropertyString("buttonClass");
        if (buttonClass == null || buttonClass.trim().isEmpty()) {
            buttonClass = "btn btn-primary";
        }
        String successMessage = getPropertyString("successMessage");
        if (successMessage == null || successMessage.trim().isEmpty()) {
            successMessage = "SMS / API call sent successfully!";
        }
        String errorMessage = getPropertyString("errorMessage");
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = "Failed to send SMS / API call.";
        }

        String provider = getPropertyString("provider");
        if (provider == null || provider.trim().isEmpty()) {
            provider = "custom";
        }

        // Custom API fields
        String apiEndpoint = getPropertyString("apiEndpoint");
        String httpMethod = getPropertyString("httpMethod");
        if (httpMethod == null || httpMethod.trim().isEmpty()) httpMethod = "POST";
        String contentType = getPropertyString("contentType");
        if (contentType == null || contentType.trim().isEmpty()) contentType = "application/json";
        String from = getPropertyString("from");
        String to = getPropertyString("to");
        String message = getPropertyString("message");
        String authHeaderName = getPropertyString("authHeaderName");
        if (authHeaderName == null || authHeaderName.trim().isEmpty()) authHeaderName = "Authorization";
        String authHeaderValue = getPropertyString("authHeaderValue");
        String customHeaders = getPropertyString("customHeaders");
        String payloadTemplate = getPropertyString("payloadTemplate");

        // Twilio fields
        String twilioAccountSid = getPropertyString("twilioAccountSid");
        String twilioAuthToken = getPropertyString("twilioAuthToken");
        String twilioTo = getPropertyString("twilioTo");
        String twilioFrom = getPropertyString("twilioFrom");
        String twilioBody = getPropertyString("twilioBody");

        // Nexmo fields
        String nexmoApiKey = getPropertyString("nexmoApiKey");
        String nexmoApiSecret = getPropertyString("nexmoApiSecret");
        String nexmoTo = getPropertyString("nexmoTo");
        String nexmoFrom = getPropertyString("nexmoFrom");
        String nexmoText = getPropertyString("nexmoText");

        // Infobip fields
        String infobipBaseUrl = getPropertyString("infobipBaseUrl");
        String infobipApiKey = getPropertyString("infobipApiKey");
        String infobipTo = getPropertyString("infobipTo");
        String infobipFrom = getPropertyString("infobipFrom");
        String infobipText = getPropertyString("infobipText");

        // Plivo fields
        String plivoAuthId = getPropertyString("plivoAuthId");
        String plivoAuthToken = getPropertyString("plivoAuthToken");
        String plivoTo = getPropertyString("plivoTo");
        String plivoFrom = getPropertyString("plivoFrom");
        String plivoText = getPropertyString("plivoText");

        // Brevo fields
        String brevoApiKey = getPropertyString("brevoApiKey");
        String brevoSender = getPropertyString("brevoSender");
        String brevoRecipient = getPropertyString("brevoRecipient");
        String brevoContent = getPropertyString("brevoContent");

        // Msg91 fields
        String msg91AuthKey = getPropertyString("msg91AuthKey");
        String msg91FlowId = getPropertyString("msg91FlowId");
        String msg91Sender = getPropertyString("msg91Sender");
        String msg91To = getPropertyString("msg91To");

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
        sb.append("<style type=\"text/css\">");
        sb.append("#sms_widget_").append(elementId).append(" { background:#ffffff; border:1px solid #d1d3e2 !important; border-radius:8px; padding:18px; margin:15px 0; box-shadow:0 0.15rem 1.75rem 0 rgba(58, 59, 69, 0.15); font-family:inherit; }\n");
        sb.append("#sms_widget_").append(elementId).append(" .row { display:flex; flex-wrap:wrap; margin-right:-8px; margin-left:-8px; }\n");
        sb.append("#sms_widget_").append(elementId).append(" .col-md-6 { flex:0 0 50%; max-width:50%; padding-right:8px; padding-left:8px; box-sizing:border-box; }\n");
        sb.append("@media (max-width: 768px) { #sms_widget_").append(elementId).append(" .col-md-6 { flex:0 0 100%; max-width:100%; } }\n");
        sb.append("#sms_widget_").append(elementId).append(" .form-group { margin-bottom:12px; }\n");
        sb.append("#sms_widget_").append(elementId).append(" label { display:block; margin-bottom:4px; font-weight:600; font-size:13px; color:#4e73df; }\n");
        sb.append("#sms_widget_").append(elementId).append(" input[type='text'], #sms_widget_").append(elementId).append(" input[type='password'], #sms_widget_").append(elementId).append(" select, #sms_widget_").append(elementId).append(" textarea { width:100%; padding:7px 10px; font-size:13px; border:1px solid #d1d3e2; border-radius:4px; box-sizing:border-box; background-color:#fff; color:#495057; transition: border-color .15s ease-in-out,box-shadow .15s ease-in-out; }\n");
        sb.append("#sms_widget_").append(elementId).append(" input:focus, #sms_widget_").append(elementId).append(" select:focus, #sms_widget_").append(elementId).append(" textarea:focus { border-color:#bac8f3; outline:0; box-shadow:0 0 0 0.2rem rgba(78,115,223,0.25); }\n");
        sb.append("#sms_widget_").append(elementId).append(" .btn { display:inline-block; font-weight:600; text-align:center; vertical-align:middle; cursor:pointer; padding:8px 18px; font-size:14px; border-radius:4px; border:none; transition: color .15s ease-in-out,background-color .15s ease-in-out; }\n");
        sb.append("#sms_widget_").append(elementId).append(" .btn-primary { background-color:#4e73df; color:#ffffff; }\n");
        sb.append("#sms_widget_").append(elementId).append(" .btn-primary:hover { background-color:#2e59d9; }\n");
        sb.append("#sms_widget_").append(elementId).append(" .alert { padding:10px 14px; border-radius:4px; font-size:13px; margin-top:12px; }\n");
        sb.append("#sms_widget_").append(elementId).append(" .alert-success { background-color:#d4edda; color:#155724; border:1px solid #c3e6cb; }\n");
        sb.append("#sms_widget_").append(elementId).append(" .alert-danger { background-color:#f8d7da; color:#721c24; border:1px solid #f5c6cb; }\n");
        sb.append("</style>");
        
        sb.append("<div id=\"sms_widget_").append(elementId).append("\" class=\"sms-notification-widget card p-3 border rounded my-3 form-cell-element form-cell\">");
        sb.append("  <h5 class=\"card-title text-primary mb-3\" style=\"font-weight:600; margin-top:0;\"><i class=\"fas fa-paper-plane mr-2\"></i>SMS & API Dispatcher</h5>");
        
        // Provider Dropdown
        sb.append("  <div class=\"form-group mb-3\">");
        sb.append("    <label for=\"").append(elementId).append("_provider\" class=\"font-weight-bold\" style=\"font-size:14px;\">SMS Provider / API Type:</label>");
        sb.append("    <select id=\"").append(elementId).append("_provider\" name=\"provider\" class=\"form-control sms-provider-select\">");
        sb.append("      <option value=\"custom\"").append("custom".equalsIgnoreCase(provider) ? " selected" : "").append(">Custom / Generic REST API</option>");
        sb.append("      <option value=\"twilio\"").append("twilio".equalsIgnoreCase(provider) ? " selected" : "").append(">Twilio SMS / WhatsApp</option>");
        sb.append("      <option value=\"nexmo\"").append("nexmo".equalsIgnoreCase(provider) ? " selected" : "").append(">Vonage / Nexmo SMS</option>");
        sb.append("      <option value=\"infobip\"").append("infobip".equalsIgnoreCase(provider) ? " selected" : "").append(">Infobip SMS</option>");
        sb.append("      <option value=\"plivo\"").append("plivo".equalsIgnoreCase(provider) ? " selected" : "").append(">Plivo SMS</option>");
        sb.append("      <option value=\"brevo\"").append("brevo".equalsIgnoreCase(provider) ? " selected" : "").append(">Brevo Transactional SMS</option>");
        sb.append("      <option value=\"msg91\"").append("msg91".equalsIgnoreCase(provider) ? " selected" : "").append(">Msg91 SMS</option>");
        sb.append("    </select>");
        sb.append("  </div>");

        // Custom API Group
        sb.append("  <div class=\"provider-group provider-custom\" style=\"display:").append("custom".equalsIgnoreCase(provider) ? "block" : "none").append(";\">");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">API Endpoint URL:</label>");
        sb.append("      <input type=\"text\" name=\"apiEndpoint\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(apiEndpoint)).append("\" placeholder=\"https://api.example.com/v1/send\" />");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">HTTP Method:</label>");
        sb.append("        <select name=\"httpMethod\" class=\"form-control form-control-sm\">");
        sb.append("          <option value=\"POST\"").append("POST".equalsIgnoreCase(httpMethod) ? " selected" : "").append(">POST</option>");
        sb.append("          <option value=\"GET\"").append("GET".equalsIgnoreCase(httpMethod) ? " selected" : "").append(">GET</option>");
        sb.append("          <option value=\"PUT\"").append("PUT".equalsIgnoreCase(httpMethod) ? " selected" : "").append(">PUT</option>");
        sb.append("          <option value=\"DELETE\"").append("DELETE".equalsIgnoreCase(httpMethod) ? " selected" : "").append(">DELETE</option>");
        sb.append("        </select>");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Content-Type:</label>");
        sb.append("        <input type=\"text\" name=\"contentType\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(contentType)).append("\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">From (Sender ID / Phone):</label>");
        sb.append("        <input type=\"text\" name=\"from\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(from)).append("\" placeholder=\"Sender ID or From Number\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">To (Recipient Phone):</label>");
        sb.append("        <input type=\"text\" name=\"to\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(to)).append("\" placeholder=\"Recipient Phone Number\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">Message Content:</label>");
        sb.append("      <textarea name=\"message\" class=\"form-control form-control-sm\" rows=\"2\" placeholder=\"Enter SMS / API message text...\">").append(escapeHtml(message)).append("</textarea>");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Auth Header Name:</label>");
        sb.append("        <input type=\"text\" name=\"authHeaderName\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(authHeaderName)).append("\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Auth Header Value:</label>");
        sb.append("        <input type=\"text\" name=\"authHeaderValue\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(authHeaderValue)).append("\" placeholder=\"Bearer token or credentials\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">Custom Headers (Header-Name: Value per line):</label>");
        sb.append("      <textarea name=\"customHeaders\" class=\"form-control form-control-sm\" rows=\"2\" placeholder=\"X-Custom-Header: value\">").append(escapeHtml(customHeaders)).append("</textarea>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">Custom Payload Template (Optional - auto-configured from To, From, Message if left blank):</label>");
        sb.append("      <textarea name=\"payloadTemplate\" class=\"form-control form-control-sm\" rows=\"2\" placeholder='Auto-configured if blank. Custom example: {\"to\":\"{to}\",\"from\":\"{from}\",\"message\":\"{message}\"}'>").append(escapeHtml(payloadTemplate)).append("</textarea>");
        sb.append("    </div>");
        sb.append("  </div>");

        // Twilio Group
        sb.append("  <div class=\"provider-group provider-twilio\" style=\"display:").append("twilio".equalsIgnoreCase(provider) ? "block" : "none").append(";\">");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Account SID:</label>");
        sb.append("        <input type=\"text\" name=\"twilioAccountSid\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(twilioAccountSid)).append("\" placeholder=\"ACxxxxxxxxxxxxxxxxxxxxxxxx\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Auth Token:</label>");
        sb.append("        <input type=\"password\" name=\"twilioAuthToken\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(twilioAuthToken)).append("\" placeholder=\"Twilio Auth Token\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">To Phone Number:</label>");
        sb.append("        <input type=\"text\" name=\"twilioTo\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(twilioTo)).append("\" placeholder=\"+1234567890\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">From Phone Number:</label>");
        sb.append("        <input type=\"text\" name=\"twilioFrom\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(twilioFrom)).append("\" placeholder=\"+10987654321\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">Message Body:</label>");
        sb.append("      <textarea name=\"twilioBody\" class=\"form-control form-control-sm\" rows=\"3\" placeholder=\"Enter message text...\">").append(escapeHtml(twilioBody)).append("</textarea>");
        sb.append("    </div>");
        sb.append("  </div>");

        // Nexmo Group
        sb.append("  <div class=\"provider-group provider-nexmo\" style=\"display:").append("nexmo".equalsIgnoreCase(provider) ? "block" : "none").append(";\">");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">API Key:</label>");
        sb.append("        <input type=\"text\" name=\"nexmoApiKey\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(nexmoApiKey)).append("\" placeholder=\"Nexmo API Key\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">API Secret:</label>");
        sb.append("        <input type=\"password\" name=\"nexmoApiSecret\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(nexmoApiSecret)).append("\" placeholder=\"Nexmo API Secret\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">To Phone Number:</label>");
        sb.append("        <input type=\"text\" name=\"nexmoTo\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(nexmoTo)).append("\" placeholder=\"1234567890\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">From Sender ID / Number:</label>");
        sb.append("        <input type=\"text\" name=\"nexmoFrom\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(nexmoFrom)).append("\" placeholder=\"MyBrand\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">Message Text:</label>");
        sb.append("      <textarea name=\"nexmoText\" class=\"form-control form-control-sm\" rows=\"3\" placeholder=\"Enter message text...\">").append(escapeHtml(nexmoText)).append("</textarea>");
        sb.append("    </div>");
        sb.append("  </div>");

        // Infobip Group
        sb.append("  <div class=\"provider-group provider-infobip\" style=\"display:").append("infobip".equalsIgnoreCase(provider) ? "block" : "none").append(";\">");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Base URL:</label>");
        sb.append("        <input type=\"text\" name=\"infobipBaseUrl\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(infobipBaseUrl)).append("\" placeholder=\"api.infobip.com\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">API Key:</label>");
        sb.append("        <input type=\"password\" name=\"infobipApiKey\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(infobipApiKey)).append("\" placeholder=\"Infobip API Key\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">To Phone Number:</label>");
        sb.append("        <input type=\"text\" name=\"infobipTo\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(infobipTo)).append("\" placeholder=\"41793026727\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">From Sender ID:</label>");
        sb.append("        <input type=\"text\" name=\"infobipFrom\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(infobipFrom)).append("\" placeholder=\"InfoSMS\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">Message Text:</label>");
        sb.append("      <textarea name=\"infobipText\" class=\"form-control form-control-sm\" rows=\"3\" placeholder=\"Enter message text...\">").append(escapeHtml(infobipText)).append("</textarea>");
        sb.append("    </div>");
        sb.append("  </div>");

        // Plivo Group
        sb.append("  <div class=\"provider-group provider-plivo\" style=\"display:").append("plivo".equalsIgnoreCase(provider) ? "block" : "none").append(";\">");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Auth ID:</label>");
        sb.append("        <input type=\"text\" name=\"plivoAuthId\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(plivoAuthId)).append("\" placeholder=\"Plivo Auth ID\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Auth Token:</label>");
        sb.append("        <input type=\"password\" name=\"plivoAuthToken\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(plivoAuthToken)).append("\" placeholder=\"Plivo Auth Token\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">To Phone Number:</label>");
        sb.append("        <input type=\"text\" name=\"plivoTo\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(plivoTo)).append("\" placeholder=\"14155552671\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">From Phone Number:</label>");
        sb.append("        <input type=\"text\" name=\"plivoFrom\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(plivoFrom)).append("\" placeholder=\"14155552672\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">Message Text:</label>");
        sb.append("      <textarea name=\"plivoText\" class=\"form-control form-control-sm\" rows=\"3\" placeholder=\"Enter message text...\">").append(escapeHtml(plivoText)).append("</textarea>");
        sb.append("    </div>");
        sb.append("  </div>");

        // Brevo Group
        sb.append("  <div class=\"provider-group provider-brevo\" style=\"display:").append("brevo".equalsIgnoreCase(provider) ? "block" : "none").append(";\">");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">API Key:</label>");
        sb.append("      <input type=\"password\" name=\"brevoApiKey\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(brevoApiKey)).append("\" placeholder=\"xkeysib-...\" />");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Sender Name / ID:</label>");
        sb.append("        <input type=\"text\" name=\"brevoSender\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(brevoSender)).append("\" placeholder=\"BrevoSMS\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Recipient Phone Number:</label>");
        sb.append("        <input type=\"text\" name=\"brevoRecipient\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(brevoRecipient)).append("\" placeholder=\"+33600000000\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"form-group mb-2\">");
        sb.append("      <label class=\"small font-weight-bold\">Message Content:</label>");
        sb.append("      <textarea name=\"brevoContent\" class=\"form-control form-control-sm\" rows=\"3\" placeholder=\"Enter message content...\">").append(escapeHtml(brevoContent)).append("</textarea>");
        sb.append("    </div>");
        sb.append("  </div>");

        // Msg91 Group
        sb.append("  <div class=\"provider-group provider-msg91\" style=\"display:").append("msg91".equalsIgnoreCase(provider) ? "block" : "none").append(";\">");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Auth Key:</label>");
        sb.append("        <input type=\"password\" name=\"msg91AuthKey\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(msg91AuthKey)).append("\" placeholder=\"Msg91 Auth Key\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Flow / Template ID:</label>");
        sb.append("        <input type=\"text\" name=\"msg91FlowId\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(msg91FlowId)).append("\" placeholder=\"Flow ID\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("    <div class=\"row\">");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">Sender ID:</label>");
        sb.append("        <input type=\"text\" name=\"msg91Sender\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(msg91Sender)).append("\" placeholder=\"MSGIND\" />");
        sb.append("      </div>");
        sb.append("      <div class=\"col-md-6 form-group mb-2\">");
        sb.append("        <label class=\"small font-weight-bold\">To Mobile Number:</label>");
        sb.append("        <input type=\"text\" name=\"msg91To\" class=\"form-control form-control-sm\" value=\"").append(escapeHtml(msg91To)).append("\" placeholder=\"919876543210\" />");
        sb.append("      </div>");
        sb.append("    </div>");
        sb.append("  </div>");

        // Action Button & Response Alert
        sb.append("  <div class=\"mt-3\">");
        sb.append("    <button type=\"button\" id=\"").append(elementId).append("_btn\" class=\"").append(buttonClass).append("\"><i class=\"fas fa-paper-plane mr-1\"></i> ").append(buttonLabel).append("</button>");
        sb.append("    <div id=\"").append(elementId).append("_alert\" class=\"alert alert-dismissible fade show mt-3\" style=\"display:none; border-radius:6px; font-size:13px;\" role=\"alert\"></div>");
        sb.append("  </div>");
        sb.append("</div>");

        // Client-side JavaScript
        sb.append("<script type=\"text/javascript\">");
        sb.append("$(document).ready(function(){\n");
        sb.append("  var wrapper = $('#sms_widget_").append(elementId).append("');\n");
        sb.append("  var providerSelect = wrapper.find('.sms-provider-select');\n");
        sb.append("  providerSelect.on('change', function(){\n");
        sb.append("    var selected = $(this).val();\n");
        sb.append("    wrapper.find('.provider-group').hide();\n");
        sb.append("    wrapper.find('.provider-' + selected).slideDown(200);\n");
        sb.append("  });\n");
        sb.append("  wrapper.find('#").append(elementId).append("_btn').click(function(e){\n");
        sb.append("    e.preventDefault();\n");
        sb.append("    var btn = $(this);\n");
        sb.append("    var alertBox = wrapper.find('#").append(elementId).append("_alert');\n");
        sb.append("    var originalHtml = btn.html();\n");
        sb.append("    btn.html('<i class=\"fas fa-spinner fa-spin mr-1\"></i> Sending...').prop('disabled', true);\n");
        sb.append("    alertBox.hide().removeClass('alert-success alert-danger').text('');\n");
        sb.append("    var postData = wrapper.closest('form').serialize();\n");
        sb.append("    $.ajax({\n");
        sb.append("      url: '").append(url).append("',\n");
        sb.append("      type: 'POST',\n");
        sb.append("      data: postData,\n");
        sb.append("      success: function(response){\n");
        sb.append("        btn.html(originalHtml).prop('disabled', false);\n");
        sb.append("        if (response && response.status >= 200 && response.status < 300) {\n");
        sb.append("          alertBox.addClass('alert-success').html('<strong>Success (' + response.status + '):</strong> ' + (response.response || '").append(escapeJson(successMessage)).append("')).slideDown();\n");
        sb.append("        } else {\n");
        sb.append("          var err = (response && response.response) ? response.response : '").append(escapeJson(errorMessage)).append("';\n");
        sb.append("          alertBox.addClass('alert-danger').html('<strong>Error (' + (response ? response.status : 500) + '):</strong> ' + err).slideDown();\n");
        sb.append("        }\n");
        sb.append("      },\n");
        sb.append("      error: function(xhr, status, error){\n");
        sb.append("        btn.html(originalHtml).prop('disabled', false);\n");
        sb.append("        var detail = (xhr.responseJSON && xhr.responseJSON.error) ? xhr.responseJSON.error : error;\n");
        sb.append("        alertBox.addClass('alert-danger').html('<strong>Request Failed:</strong> ' + detail).slideDown();\n");
        sb.append("      }\n");
        sb.append("    });\n");
        sb.append("  });\n");
        sb.append("});\n");
        sb.append("</script>");

        return sb.toString();
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            Map<String, Object> mergedProperties = new java.util.HashMap<>();
            
            // Extract request parameters sent from front-end form controls
            Map<String, String[]> paramMap = request.getParameterMap();
            if (paramMap != null) {
                for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().length > 0) {
                        mergedProperties.put(entry.getKey(), entry.getValue()[0]);
                    }
                }
            }

            String appId = request.getParameter("appId");
            String appVersion = request.getParameter("appVersion");
            String formDefId = request.getParameter("formDefId");
            String elementId = request.getParameter("elementId");

            AppDefinition appDef = null;
            if (appId != null && !appId.isEmpty()) {
                org.joget.apps.app.service.AppService appService = (org.joget.apps.app.service.AppService) AppUtil.getApplicationContext().getBean("appService");
                appDef = appService.getAppDefinition(appId, appVersion);
            }
            if (appDef == null) {
                appDef = AppUtil.getCurrentAppDefinition();
            }

            // Merge element saved properties as fallbacks if available
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
                                String keyStr = k.toString();
                                if (!mergedProperties.containsKey(keyStr) || mergedProperties.get(keyStr) == null || mergedProperties.get(keyStr).toString().isEmpty()) {
                                    mergedProperties.put(keyStr, elemProps.get(k));
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    LogUtil.warn(getClassName(), "Could not merge form element properties: " + ex.getMessage());
                }
            }

            // Merge global plugin default properties from Configurable Plugins
            try {
                mergedProperties = AppPluginUtil.getDefaultProperties(this, mergedProperties, appDef, null);
            } catch (Exception ex) {
                LogUtil.warn(getClassName(), "Could not merge global plugin default properties: " + ex.getMessage());
            }

            smsNotificationTool tool = new smsNotificationTool();
            Map<String, Object> apiResult = tool.performApiCallAndAuditLog(mergedProperties, null, appDef);

            response.setContentType("application/json");
            int statusCode = 200;
            if (apiResult != null && apiResult.get("status") != null) {
                try {
                    statusCode = Integer.parseInt(apiResult.get("status").toString());
                } catch (Exception ignored) {}
            }
            response.setStatus(statusCode >= 200 && statusCode < 600 ? statusCode : 200);
            String responseStr = apiResult != null ? (String) apiResult.get("response") : "";
            response.getWriter().write("{\"status\":" + statusCode + ", \"response\":\"" + escapeJson(responseStr) + "\"}");
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error in SMS Form Element webservice");
            response.setStatus(500);
            response.getWriter().write("{\"status\":500, \"error\":\"Internal Server Error: " + escapeJson(e.getMessage()) + "\"}");
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
