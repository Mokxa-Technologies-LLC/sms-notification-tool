package org.joget.marketplace;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class smsNotificationTool extends DefaultApplicationPlugin implements PropertyEditable {

    // Cache table schema checks to optimize DB execution per table name
    private static final Map<String, Boolean> VERIFIED_TABLES = new ConcurrentHashMap<>();

    // Asynchronous execution thread pool for background API execution
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);

    // Regex patterns for automatic sensitive data redaction
    private static final Pattern JSON_SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(\"?(?:api[_-]?key|api[_-]?secret|password|authtoken|authkey|secret|token|accountSid)\"?\\s*:\\s*\")[^\"]+(\")");
    private static final Pattern FORM_SENSITIVE_PATTERN = Pattern
            .compile("(?i)(&|^)(api[_-]?key|api[_-]?secret|password|authtoken|authkey|secret|token)=([^&]+)");
    private static final Pattern AUTH_HEADER_PATTERN = Pattern
            .compile("(?i)(Basic|Bearer|App|IBSS)\\s+[A-Za-z0-9+/=_-]+");

    @Override
    public String getName() {
        return "SMS Notification Tool";
    }

    @Override
    public String getVersion() {
        return "1.4";
    }

    @Override
    public String getDescription() {
        return "Executes SMS / REST API calls and automatically logs request/response audit trail to a database table. Features Strategy Pattern providers (Twilio, Nexmo, Infobip, Plivo, Brevo, Msg91), async execution, sensitive data masking, custom headers table, and Joget workflow variable mapping.";
    }

    @Override
    public String getLabel() {
        return "SMS Notification Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/smsNotificationTool.json", null, true,
                "message/smsNotificationTool");
    }

    // Helper method to safely escape HTML for datalist rendering
    private String escapeHtml(String input) {
        if (input == null)
            return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    // Helper to safely fetch properties across merged Map and fallback
    private String getPropString(Map properties, String key) {
        if (properties != null && properties.containsKey(key)) {
            Object val = properties.get(key);
            if (val != null && !val.toString().trim().isEmpty()) {
                return val.toString().trim();
            }
        }
        return "";
    }

    private String getToProp(Map properties, String providerKey, WorkflowAssignment wfAssignment) {
        String val = getPropString(properties, providerKey);
        if (val.isEmpty()) {
            val = getPropString(properties, "to");
        }
        if (val.isEmpty()) {
            val = getPropString(properties, "recipient");
        }
        return AppUtil.processHashVariable(val, wfAssignment, null, null);
    }

    private String getFromProp(Map properties, String providerKey, WorkflowAssignment wfAssignment) {
        String val = getPropString(properties, providerKey);
        if (val.isEmpty()) {
            val = getPropString(properties, "from");
        }
        if (val.isEmpty()) {
            val = getPropString(properties, "sender");
        }
        return AppUtil.processHashVariable(val, wfAssignment, null, null);
    }

    private String getMessageProp(Map properties, String providerKey, WorkflowAssignment wfAssignment) {
        String val = getPropString(properties, providerKey);
        if (val.isEmpty()) {
            val = getPropString(properties, "message");
        }
        if (val.isEmpty()) {
            val = getPropString(properties, "body");
        }
        if (val.isEmpty()) {
            val = getPropString(properties, "text");
        }
        if (val.isEmpty()) {
            val = getPropString(properties, "content");
        }
        return AppUtil.processHashVariable(val, wfAssignment, null, null);
    }

    // Sensitive data masking utility
    private String maskSensitiveData(String input) {
        if (input == null || input.isEmpty())
            return "";
        String masked = JSON_SENSITIVE_PATTERN.matcher(input).replaceAll("$1********$2");
        masked = FORM_SENSITIVE_PATTERN.matcher(masked).replaceAll("$1$2=********");
        masked = AUTH_HEADER_PATTERN.matcher(masked).replaceAll("$1 ********");
        return masked;
    }

    // Provider Request Specification model
    private static class RequestSpec {
        String httpMethod = "POST";
        String contentType = "application/json";
        String endpoint = "";
        String authHeaderName = "Authorization";
        String authHeaderValue = "";
        String payload = "";
        Map<String, String> extraHeaders = new HashMap<>();
    }

    // Strategy Pattern interface for API providers
    private interface ProviderHandler {
        RequestSpec buildRequest(Map mergedProperties, WorkflowAssignment wfAssignment);
    }

    // Strategy implementation for Twilio
    private class TwilioHandler implements ProviderHandler {
        @Override
        public RequestSpec buildRequest(Map properties, WorkflowAssignment wfAssignment) {
            RequestSpec spec = new RequestSpec();
            spec.httpMethod = getPropString(properties, "httpMethod");
            if (spec.httpMethod.isEmpty())
                spec.httpMethod = "POST";

            spec.contentType = getPropString(properties, "contentType");
            if (spec.contentType.isEmpty())
                spec.contentType = "application/x-www-form-urlencoded";

            String accountSid = AppUtil.processHashVariable(getPropString(properties, "twilioAccountSid"), wfAssignment,
                    null, null);
            String authToken = getPropString(properties, "twilioAuthToken");
            authToken = AppUtil.processHashVariable(authToken, wfAssignment, null, null);

            if (authToken != null && !authToken.isEmpty()) {
                authToken = SecurityUtil.decrypt(authToken);
                authToken = AppUtil.processHashVariable(authToken, wfAssignment, null, null);
            }

            String twilioEndpointOpt = getPropString(properties, "twilioEndpoint");
            String apiEndpoint = getPropString(properties, "apiEndpoint");

            if ("custom".equalsIgnoreCase(twilioEndpointOpt)) {
                spec.endpoint = getPropString(properties, "twilioCustomEndpoint");
            } else if (!twilioEndpointOpt.isEmpty()) {
                spec.endpoint = twilioEndpointOpt.replace("{accountSid}", accountSid);
            } else if (!apiEndpoint.isEmpty()) {
                spec.endpoint = apiEndpoint;
            } else {
                spec.endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
            }

            String configuredAuthVal = getPropString(properties, "authHeaderValue");
            if (!configuredAuthVal.isEmpty()) {
                spec.authHeaderValue = configuredAuthVal;
            } else if (authToken != null && !authToken.isEmpty()) {
                if (authToken.startsWith("Basic ") || authToken.startsWith("Bearer ")) {
                    spec.authHeaderValue = authToken;
                } else if (authToken.contains(":")) {
                    spec.authHeaderValue = "Basic "
                            + Base64.getEncoder().encodeToString(authToken.getBytes(StandardCharsets.UTF_8));
                } else if (!accountSid.isEmpty()) {
                    String pair = accountSid + ":" + authToken;
                    spec.authHeaderValue = "Basic "
                            + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
                } else {
                    spec.authHeaderValue = authToken;
                }
            }

            String payloadTemplate = getPropString(properties, "payloadTemplate");
            if (!payloadTemplate.isEmpty()) {
                spec.payload = payloadTemplate;
            } else {
                String to = getToProp(properties, "twilioTo", wfAssignment);
                String from = getFromProp(properties, "twilioFrom", wfAssignment);
                String body = getMessageProp(properties, "twilioBody", wfAssignment);

                try {
                    StringBuilder sb = new StringBuilder();
                    if (!to.isEmpty())
                        sb.append("To=").append(URLEncoder.encode(to, "UTF-8"));
                    if (!from.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append("&");
                        sb.append("From=").append(URLEncoder.encode(from, "UTF-8"));
                    }
                    if (!body.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append("&");
                        sb.append("Body=").append(URLEncoder.encode(body, "UTF-8"));
                    }
                    spec.payload = sb.toString();
                } catch (Exception e) {
                    LogUtil.error(getClassName(), e, "Error encoding Twilio payload");
                }
            }
            return spec;
        }
    }

    // Strategy implementation for Nexmo / Vonage
    private class NexmoHandler implements ProviderHandler {
        @Override
        public RequestSpec buildRequest(Map properties, WorkflowAssignment wfAssignment) {
            RequestSpec spec = new RequestSpec();
            spec.httpMethod = getPropString(properties, "httpMethod");
            if (spec.httpMethod.isEmpty())
                spec.httpMethod = "POST";

            spec.contentType = getPropString(properties, "contentType");
            if (spec.contentType.isEmpty())
                spec.contentType = "application/json";

            String apiKey = AppUtil.processHashVariable(getPropString(properties, "nexmoApiKey"), wfAssignment, null,
                    null);
            String apiSecret = getPropString(properties, "nexmoApiSecret");
            apiSecret = AppUtil.processHashVariable(apiSecret, wfAssignment, null, null);

            if (apiSecret != null && !apiSecret.isEmpty()) {
                apiSecret = SecurityUtil.decrypt(apiSecret);
                apiSecret = AppUtil.processHashVariable(apiSecret, wfAssignment, null, null);
            }

            String nexmoEndpointOpt = getPropString(properties, "nexmoEndpoint");
            String apiEndpoint = getPropString(properties, "apiEndpoint");

            if ("custom".equalsIgnoreCase(nexmoEndpointOpt)) {
                spec.endpoint = getPropString(properties, "nexmoCustomEndpoint");
            } else if (!nexmoEndpointOpt.isEmpty()) {
                spec.endpoint = nexmoEndpointOpt;
            } else if (!apiEndpoint.isEmpty()) {
                spec.endpoint = apiEndpoint;
            } else {
                spec.endpoint = "https://rest.nexmo.com/sms/json";
            }

            String payloadTemplate = getPropString(properties, "payloadTemplate");
            if (!payloadTemplate.isEmpty()) {
                spec.payload = payloadTemplate;
            } else {
                String to = getToProp(properties, "nexmoTo", wfAssignment);
                String from = getFromProp(properties, "nexmoFrom", wfAssignment);
                String text = getMessageProp(properties, "nexmoText", wfAssignment);

                if (to.startsWith("+"))
                    to = to.substring(1);
                to = to.replaceAll("[\\s\\-\\(\\)]", "");

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"api_key\":\"").append(escapeJson(apiKey)).append("\",");
                json.append("\"api_secret\":\"").append(escapeJson(apiSecret)).append("\",");
                json.append("\"to\":\"").append(escapeJson(to)).append("\",");
                json.append("\"from\":\"").append(escapeJson(from)).append("\",");
                json.append("\"text\":\"").append(escapeJson(text)).append("\"");
                json.append("}");
                spec.payload = json.toString();
            }
            return spec;
        }
    }

    // Strategy implementation for Infobip
    private class InfobipHandler implements ProviderHandler {
        @Override
        public RequestSpec buildRequest(Map properties, WorkflowAssignment wfAssignment) {
            RequestSpec spec = new RequestSpec();
            spec.httpMethod = getPropString(properties, "httpMethod");
            if (spec.httpMethod.isEmpty())
                spec.httpMethod = "POST";

            spec.contentType = getPropString(properties, "contentType");
            if (spec.contentType.isEmpty())
                spec.contentType = "application/json";

            String apiKey = getPropString(properties, "infobipApiKey");
            String baseUrl = AppUtil.processHashVariable(getPropString(properties, "infobipBaseUrl"), wfAssignment,
                    null, null);

            apiKey = AppUtil.processHashVariable(apiKey, wfAssignment, null, null);
            if (apiKey != null && !apiKey.isEmpty()) {
                apiKey = SecurityUtil.decrypt(apiKey);
                apiKey = AppUtil.processHashVariable(apiKey, wfAssignment, null, null);
            }

            if (baseUrl.isEmpty())
                baseUrl = "api.infobip.com";
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                baseUrl = "https://" + baseUrl;
            }

            String infobipEndpointOpt = getPropString(properties, "infobipEndpoint");
            String apiEndpoint = getPropString(properties, "apiEndpoint");

            if ("custom".equalsIgnoreCase(infobipEndpointOpt)) {
                spec.endpoint = getPropString(properties, "infobipCustomEndpoint");
            } else if (!infobipEndpointOpt.isEmpty()) {
                spec.endpoint = infobipEndpointOpt;
            } else if (!apiEndpoint.isEmpty()) {
                spec.endpoint = apiEndpoint;
            } else {
                spec.endpoint = baseUrl + "/sms/2/text/single";
            }

            String configuredAuthVal = getPropString(properties, "authHeaderValue");
            if (!configuredAuthVal.isEmpty()) {
                spec.authHeaderValue = configuredAuthVal;
            } else if (apiKey != null && !apiKey.isEmpty()) {
                spec.authHeaderValue = apiKey.startsWith("App ") || apiKey.startsWith("IBSS ") ? apiKey
                        : "App " + apiKey;
            }

            String payloadTemplate = getPropString(properties, "payloadTemplate");
            if (!payloadTemplate.isEmpty()) {
                spec.payload = payloadTemplate;
            } else {
                String to = getToProp(properties, "infobipTo", wfAssignment);
                String from = getFromProp(properties, "infobipFrom", wfAssignment);
                String text = getMessageProp(properties, "infobipText", wfAssignment);

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"messages\":[{");
                if (!from.isEmpty())
                    json.append("\"from\":\"").append(escapeJson(from)).append("\",");
                json.append("\"destinations\":[{\"to\":\"").append(escapeJson(to)).append("\"}],");
                json.append("\"text\":\"").append(escapeJson(text)).append("\"");
                json.append("}]}");
                spec.payload = json.toString();
            }
            return spec;
        }
    }

    // Strategy implementation for Plivo
    private class PlivoHandler implements ProviderHandler {
        @Override
        public RequestSpec buildRequest(Map properties, WorkflowAssignment wfAssignment) {
            RequestSpec spec = new RequestSpec();
            spec.httpMethod = getPropString(properties, "httpMethod");
            if (spec.httpMethod.isEmpty())
                spec.httpMethod = "POST";

            spec.contentType = getPropString(properties, "contentType");
            if (spec.contentType.isEmpty())
                spec.contentType = "application/json";

            String authId = AppUtil.processHashVariable(getPropString(properties, "plivoAuthId"), wfAssignment, null,
                    null);
            String authToken = getPropString(properties, "plivoAuthToken");
            authToken = AppUtil.processHashVariable(authToken, wfAssignment, null, null);

            if (authToken != null && !authToken.isEmpty()) {
                authToken = SecurityUtil.decrypt(authToken);
                authToken = AppUtil.processHashVariable(authToken, wfAssignment, null, null);
            }

            String plivoEndpointOpt = getPropString(properties, "plivoEndpoint");
            String apiEndpoint = getPropString(properties, "apiEndpoint");

            if ("custom".equalsIgnoreCase(plivoEndpointOpt)) {
                spec.endpoint = getPropString(properties, "plivoCustomEndpoint");
            } else if (!plivoEndpointOpt.isEmpty()) {
                spec.endpoint = plivoEndpointOpt.replace("{authId}", authId);
            } else if (!apiEndpoint.isEmpty()) {
                spec.endpoint = apiEndpoint;
            } else {
                spec.endpoint = "https://api.plivo.com/v1/Account/" + authId + "/Message/";
            }

            String configuredAuthVal = getPropString(properties, "authHeaderValue");
            if (!configuredAuthVal.isEmpty()) {
                spec.authHeaderValue = configuredAuthVal;
            } else if (authToken != null && !authToken.isEmpty()) {
                String pair = authId + ":" + authToken;
                spec.authHeaderValue = "Basic "
                        + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
            }

            String payloadTemplate = getPropString(properties, "payloadTemplate");
            if (!payloadTemplate.isEmpty()) {
                spec.payload = payloadTemplate;
            } else {
                String to = getToProp(properties, "plivoTo", wfAssignment);
                String from = getFromProp(properties, "plivoFrom", wfAssignment);
                String text = getMessageProp(properties, "plivoText", wfAssignment);

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"src\":\"").append(escapeJson(from)).append("\",");
                json.append("\"dst\":\"").append(escapeJson(to)).append("\",");
                json.append("\"text\":\"").append(escapeJson(text)).append("\"");
                json.append("}");
                spec.payload = json.toString();
            }
            return spec;
        }
    }

    // Strategy implementation for Brevo
    private class BrevoHandler implements ProviderHandler {
        @Override
        public RequestSpec buildRequest(Map properties, WorkflowAssignment wfAssignment) {
            RequestSpec spec = new RequestSpec();
            spec.httpMethod = getPropString(properties, "httpMethod");
            if (spec.httpMethod.isEmpty())
                spec.httpMethod = "POST";

            spec.contentType = getPropString(properties, "contentType");
            if (spec.contentType.isEmpty())
                spec.contentType = "application/json";

            String apiKey = getPropString(properties, "brevoApiKey");
            apiKey = AppUtil.processHashVariable(apiKey, wfAssignment, null, null);
            if (apiKey != null && !apiKey.isEmpty()) {
                apiKey = SecurityUtil.decrypt(apiKey);
                apiKey = AppUtil.processHashVariable(apiKey, wfAssignment, null, null);
            }

            String brevoEndpointOpt = getPropString(properties, "brevoEndpoint");
            String apiEndpoint = getPropString(properties, "apiEndpoint");

            if ("custom".equalsIgnoreCase(brevoEndpointOpt)) {
                spec.endpoint = getPropString(properties, "brevoCustomEndpoint");
            } else if (!brevoEndpointOpt.isEmpty()) {
                spec.endpoint = brevoEndpointOpt;
            } else if (!apiEndpoint.isEmpty()) {
                spec.endpoint = apiEndpoint;
            } else {
                spec.endpoint = "https://api.brevo.com/v3/transactionalSMS/sms";
            }

            spec.authHeaderName = getPropString(properties, "authHeaderName");
            if (spec.authHeaderName.isEmpty() || "Authorization".equalsIgnoreCase(spec.authHeaderName)) {
                spec.authHeaderName = "api-key";
            }

            spec.authHeaderValue = getPropString(properties, "authHeaderValue");
            if (spec.authHeaderValue.isEmpty()) {
                spec.authHeaderValue = apiKey;
            }

            String payloadTemplate = getPropString(properties, "payloadTemplate");
            if (!payloadTemplate.isEmpty()) {
                spec.payload = payloadTemplate;
            } else {
                String sender = getFromProp(properties, "brevoSender", wfAssignment);
                String recipient = getToProp(properties, "brevoRecipient", wfAssignment);
                String content = getMessageProp(properties, "brevoContent", wfAssignment);

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"sender\":\"").append(escapeJson(sender)).append("\",");
                json.append("\"recipient\":\"").append(escapeJson(recipient)).append("\",");
                json.append("\"content\":\"").append(escapeJson(content)).append("\"");
                json.append("}");
                spec.payload = json.toString();
            }
            return spec;
        }
    }

    // Strategy implementation for Msg91
    private class Msg91Handler implements ProviderHandler {
        @Override
        public RequestSpec buildRequest(Map properties, WorkflowAssignment wfAssignment) {
            RequestSpec spec = new RequestSpec();
            spec.httpMethod = getPropString(properties, "httpMethod");
            if (spec.httpMethod.isEmpty())
                spec.httpMethod = "POST";

            spec.contentType = getPropString(properties, "contentType");
            if (spec.contentType.isEmpty())
                spec.contentType = "application/json";

            String authKey = getPropString(properties, "msg91AuthKey");
            authKey = AppUtil.processHashVariable(authKey, wfAssignment, null, null);
            if (authKey != null && !authKey.isEmpty()) {
                authKey = SecurityUtil.decrypt(authKey);
                authKey = AppUtil.processHashVariable(authKey, wfAssignment, null, null);
            }

            String msg91EndpointOpt = getPropString(properties, "msg91Endpoint");
            String apiEndpoint = getPropString(properties, "apiEndpoint");

            if ("custom".equalsIgnoreCase(msg91EndpointOpt)) {
                spec.endpoint = getPropString(properties, "msg91CustomEndpoint");
            } else if (!msg91EndpointOpt.isEmpty()) {
                spec.endpoint = msg91EndpointOpt;
            } else if (!apiEndpoint.isEmpty()) {
                spec.endpoint = apiEndpoint;
            } else {
                spec.endpoint = "https://control.msg91.com/api/v5/flow/";
            }

            spec.authHeaderName = getPropString(properties, "authHeaderName");
            if (spec.authHeaderName.isEmpty() || "Authorization".equalsIgnoreCase(spec.authHeaderName)) {
                spec.authHeaderName = "authkey";
            }

            spec.authHeaderValue = getPropString(properties, "authHeaderValue");
            if (spec.authHeaderValue.isEmpty()) {
                spec.authHeaderValue = authKey;
            }

            String payloadTemplate = getPropString(properties, "payloadTemplate");
            if (!payloadTemplate.isEmpty()) {
                spec.payload = payloadTemplate;
            } else {
                String flowId = AppUtil.processHashVariable(getPropString(properties, "msg91FlowId"), wfAssignment,
                        null, null);
                String sender = getFromProp(properties, "msg91Sender", wfAssignment);
                String to = getToProp(properties, "msg91To", wfAssignment);

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"template_id\":\"").append(escapeJson(flowId)).append("\",");
                json.append("\"sender\":\"").append(escapeJson(sender)).append("\",");
                json.append("\"recipients\":[{\"mobiles\":\"").append(escapeJson(to)).append("\"}]");
                json.append("}");
                spec.payload = json.toString();
            }
            return spec;
        }
    }

    // Strategy implementation for Custom Generic REST API
    private class CustomGenericHandler implements ProviderHandler {
        @Override
        public RequestSpec buildRequest(Map properties, WorkflowAssignment wfAssignment) {
            RequestSpec spec = new RequestSpec();
            spec.endpoint = getPropString(properties, "apiEndpoint");
            spec.httpMethod = getPropString(properties, "httpMethod");
            if (spec.httpMethod.isEmpty())
                spec.httpMethod = "POST";

            spec.contentType = getPropString(properties, "contentType");
            if (spec.contentType.isEmpty())
                spec.contentType = "application/x-www-form-urlencoded";

            spec.authHeaderName = getPropString(properties, "authHeaderName");
            if (spec.authHeaderName.isEmpty())
                spec.authHeaderName = "Authorization";

            spec.authHeaderValue = getPropString(properties, "authHeaderValue");
            if (spec.authHeaderValue != null && !spec.authHeaderValue.isEmpty()) {
                spec.authHeaderValue = SecurityUtil.decrypt(spec.authHeaderValue);
            }

            String payloadTemplate = getPropString(properties, "payloadTemplate");
            if (!payloadTemplate.isEmpty()) {
                spec.payload = payloadTemplate;
            } else {
                String to = getToProp(properties, "to", wfAssignment);
                String from = getFromProp(properties, "from", wfAssignment);
                String msg = getMessageProp(properties, "message", wfAssignment);

                if (spec.contentType.toLowerCase().contains("json")) {
                    StringBuilder json = new StringBuilder();
                    json.append("{");
                    json.append("\"to\":\"").append(escapeJson(to)).append("\",");
                    json.append("\"from\":\"").append(escapeJson(from)).append("\",");
                    json.append("\"message\":\"").append(escapeJson(msg)).append("\"");
                    json.append("}");
                    spec.payload = json.toString();
                } else {
                    try {
                        StringBuilder sb = new StringBuilder();
                        if (!to.isEmpty())
                            sb.append("to=").append(URLEncoder.encode(to, "UTF-8"));
                        if (!from.isEmpty()) {
                            if (sb.length() > 0)
                                sb.append("&");
                            sb.append("from=").append(URLEncoder.encode(from, "UTF-8"));
                        }
                        if (!msg.isEmpty()) {
                            if (sb.length() > 0)
                                sb.append("&");
                            sb.append("message=").append(URLEncoder.encode(msg, "UTF-8"));
                        }
                        spec.payload = sb.toString();
                    } catch (Exception e) {
                        LogUtil.error(getClassName(), e, "Error encoding Custom payload");
                    }
                }
            }

            // Parse grid custom headers (Table UI)
            Object headersGridObj = properties.get("headersGrid");
            if (headersGridObj != null) {
                Object[] rows = null;
                if (headersGridObj instanceof Object[]) {
                    rows = (Object[]) headersGridObj;
                } else if (headersGridObj instanceof Map[]) {
                    rows = (Map[]) headersGridObj;
                }
                if (rows != null) {
                    for (Object row : rows) {
                        if (row instanceof Map) {
                            Map map = (Map) row;
                            String hName = getPropString(map, "headerName");
                            if (hName.isEmpty())
                                hName = getPropString(map, "name");
                            String hValue = getPropString(map, "headerValue");
                            if (hValue.isEmpty())
                                hValue = getPropString(map, "value");

                            hName = AppUtil.processHashVariable(hName, wfAssignment, null, null);
                            hValue = AppUtil.processHashVariable(hValue, wfAssignment, null, null);

                            if (!hName.isEmpty()) {
                                spec.extraHeaders.put(hName, hValue);
                            }
                        }
                    }
                } else if (headersGridObj instanceof java.util.Collection) {
                    for (Object row : (java.util.Collection) headersGridObj) {
                        if (row instanceof Map) {
                            Map map = (Map) row;
                            String hName = getPropString(map, "headerName");
                            if (hName.isEmpty())
                                hName = getPropString(map, "name");
                            String hValue = getPropString(map, "headerValue");
                            if (hValue.isEmpty())
                                hValue = getPropString(map, "value");

                            hName = AppUtil.processHashVariable(hName, wfAssignment, null, null);
                            hValue = AppUtil.processHashVariable(hValue, wfAssignment, null, null);

                            if (!hName.isEmpty()) {
                                spec.extraHeaders.put(hName, hValue);
                            }
                        }
                    }
                }
            }

            // Parse additional custom headers (Text UI)
            String customHeadersRaw = getPropString(properties, "customHeaders");
            if (!customHeadersRaw.isEmpty()) {
                customHeadersRaw = AppUtil.processHashVariable(customHeadersRaw, wfAssignment, null, null);
                String[] lines = customHeadersRaw.split("\\r?\\n");
                for (String line : lines) {
                    if (line.contains(":")) {
                        String[] parts = line.split(":", 2);
                        if (parts.length == 2 && !parts[0].trim().isEmpty()) {
                            spec.extraHeaders.put(parts[0].trim(), parts[1].trim());
                        }
                    }
                }
            }

            return spec;
        }
    }

    // Universal custom headers parser for both Grid Table and Textarea across all
    // providers
    private void parseCustomHeaders(Map properties, WorkflowAssignment wfAssignment, RequestSpec spec) {
        if (properties == null || spec == null)
            return;

        // Parse grid custom headers (Table UI)
        Object headersGridObj = properties.get("headersGrid");
        if (headersGridObj != null) {
            Object[] rows = null;
            if (headersGridObj instanceof Object[]) {
                rows = (Object[]) headersGridObj;
            } else if (headersGridObj instanceof Map[]) {
                rows = (Map[]) headersGridObj;
            }
            if (rows != null) {
                for (Object row : rows) {
                    if (row instanceof Map) {
                        Map map = (Map) row;
                        String hName = getPropString(map, "headerName");
                        if (hName.isEmpty())
                            hName = getPropString(map, "name");
                        String hValue = getPropString(map, "headerValue");
                        if (hValue.isEmpty())
                            hValue = getPropString(map, "value");

                        hName = AppUtil.processHashVariable(hName, wfAssignment, null, null);
                        hValue = AppUtil.processHashVariable(hValue, wfAssignment, null, null);

                        if (!hName.isEmpty()) {
                            spec.extraHeaders.put(hName, hValue);
                        }
                    }
                }
            } else if (headersGridObj instanceof java.util.Collection) {
                for (Object row : (java.util.Collection) headersGridObj) {
                    if (row instanceof Map) {
                        Map map = (Map) row;
                        String hName = getPropString(map, "headerName");
                        if (hName.isEmpty())
                            hName = getPropString(map, "name");
                        String hValue = getPropString(map, "headerValue");
                        if (hValue.isEmpty())
                            hValue = getPropString(map, "value");

                        hName = AppUtil.processHashVariable(hName, wfAssignment, null, null);
                        hValue = AppUtil.processHashVariable(hValue, wfAssignment, null, null);

                        if (!hName.isEmpty()) {
                            spec.extraHeaders.put(hName, hValue);
                        }
                    }
                }
            }
        }

        // Parse additional custom headers (Text UI)
        String customHeadersRaw = getPropString(properties, "customHeaders");
        if (!customHeadersRaw.isEmpty()) {
            customHeadersRaw = AppUtil.processHashVariable(customHeadersRaw, wfAssignment, null, null);
            String[] lines = customHeadersRaw.split("\\r?\\n");
            for (String line : lines) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2 && !parts[0].trim().isEmpty()) {
                        spec.extraHeaders.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        }
    }

    @Override
    public Object execute(Map properties) {
        LogUtil.info(getClassName(), "=== Starting Generic API Process Tool Execution ===");

        WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        // 1. Joget DX9 Property Resolution (Tool -> App -> Global Hierarchy)
        Map mergedProperties = new HashMap();
        try {
            mergedProperties = AppPluginUtil.getDefaultProperties(this, properties, appDef, wfAssignment);
        } catch (Exception e) {
            LogUtil.warn(getClassName(), "Could not retrieve merged default properties: " + e.getMessage());
            mergedProperties = properties;
        }

        if (mergedProperties == null) {
            mergedProperties = properties;
        }

        String asyncExecution = getPropString(mergedProperties, "asyncExecution");
        if ("true".equalsIgnoreCase(asyncExecution)) {
            LogUtil.info(getClassName(), "Launching API Execution asynchronously in background thread.");
            final Map finalMergedProps = mergedProperties;
            final AppDefinition finalAppDef = appDef;
            EXECUTOR_SERVICE.submit(() -> performApiCallAndAuditLog(finalMergedProps, wfAssignment, finalAppDef));
            return null;
        } else {
            return performApiCallAndAuditLog(mergedProperties, wfAssignment, appDef);
        }
    }

    public Map<String, Object> performApiCallAndAuditLog(Map mergedProperties, WorkflowAssignment wfAssignment, AppDefinition appDef) {
        String provider = getPropString(mergedProperties, "provider");
        if (provider.isEmpty())
            provider = "custom";

        // 2. Dispatch to Provider Strategy Handler
        ProviderHandler handler;
        if ("twilio".equalsIgnoreCase(provider)) {
            handler = new TwilioHandler();
        } else if ("nexmo".equalsIgnoreCase(provider)) {
            handler = new NexmoHandler();
        } else if ("infobip".equalsIgnoreCase(provider)) {
            handler = new InfobipHandler();
        } else if ("plivo".equalsIgnoreCase(provider)) {
            handler = new PlivoHandler();
        } else if ("brevo".equalsIgnoreCase(provider)) {
            handler = new BrevoHandler();
        } else if ("msg91".equalsIgnoreCase(provider)) {
            handler = new Msg91Handler();
        } else {
            handler = new CustomGenericHandler();
        }

        RequestSpec spec = handler.buildRequest(mergedProperties, wfAssignment);
        parseCustomHeaders(mergedProperties, wfAssignment, spec);

        // Process Hash Variables across Endpoint, Payload, and Auth Headers
        spec.endpoint = AppUtil.processHashVariable(spec.endpoint, wfAssignment, null, null);
        spec.payload = AppUtil.processHashVariable(spec.payload, wfAssignment, null, null);
        spec.authHeaderValue = AppUtil.processHashVariable(spec.authHeaderValue, wfAssignment, null, null);

        boolean enableDataMasking = !"false".equalsIgnoreCase(getPropString(mergedProperties, "enableDataMasking"));
        String displayEndpoint = spec.endpoint;
        String displayPayload = enableDataMasking ? maskSensitiveData(spec.payload) : spec.payload;

        LogUtil.info(getClassName(),
                "Target Endpoint: [" + spec.httpMethod + "] " + displayEndpoint + " (Provider: " + provider + ")");

        int responseCode = 0;
        String apiResponseOutput = "";
        HttpURLConnection conn = null;

        // 3. HTTP Request Execution
        try {
            URL url = new URL(spec.endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(spec.httpMethod);
            conn.setConnectTimeout(10000); // 10s connect timeout
            conn.setReadTimeout(30000); // 30s read timeout
            conn.setDoOutput("POST".equalsIgnoreCase(spec.httpMethod) || "PUT".equalsIgnoreCase(spec.httpMethod));

            if (spec.contentType != null && !spec.contentType.isEmpty()) {
                conn.setRequestProperty("Content-Type", spec.contentType);
            }
            if (spec.authHeaderName != null && !spec.authHeaderName.isEmpty() && spec.authHeaderValue != null
                    && !spec.authHeaderValue.isEmpty()) {
                conn.setRequestProperty(spec.authHeaderName, spec.authHeaderValue);
            }
            for (Map.Entry<String, String> header : spec.extraHeaders.entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }

            if ("POST".equalsIgnoreCase(spec.httpMethod) || "PUT".equalsIgnoreCase(spec.httpMethod)) {
                if (spec.payload != null && !spec.payload.isEmpty()) {
                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = spec.payload.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                }
            }

            responseCode = conn.getResponseCode();
            LogUtil.info(getClassName(), "HTTP Response Code: " + responseCode);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                StringBuilder responseBuilder = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    responseBuilder.append(inputLine);
                }
                apiResponseOutput = responseBuilder.toString();
            }

            String displayResponse = enableDataMasking ? maskSensitiveData(apiResponseOutput) : apiResponseOutput;

            if (responseCode >= 200 && responseCode < 300) {
                LogUtil.info(getClassName(), "API Call Succeeded.");
            } else {
                LogUtil.error(getClassName(), null, "API Call Failed (" + responseCode + "): " + displayResponse);
            }

        } catch (Exception e) {
            apiResponseOutput = "CRITICAL ERROR: " + e.getMessage();
            LogUtil.error(getClassName(), e, "CRITICAL ERROR during HTTP execution.");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        // 4. Store Response Code and Response Body into Workflow Variables
        String responseVarCode = getPropString(mergedProperties, "responseVarCode");
        String responseVarOutput = getPropString(mergedProperties, "responseVarOutput");

        if (wfAssignment != null && (!responseVarCode.isEmpty() || !responseVarOutput.isEmpty())) {
            try {
                WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext()
                        .getBean("workflowManager");
                if (workflowManager != null && wfAssignment.getActivityId() != null) {
                    if (!responseVarCode.isEmpty()) {
                        workflowManager.activityVariable(wfAssignment.getActivityId(), responseVarCode,
                                String.valueOf(responseCode));
                        LogUtil.info(getClassName(),
                                "Stored Status Code (" + responseCode + ") into Workflow Variable: " + responseVarCode);
                    }
                    if (!responseVarOutput.isEmpty()) {
                        workflowManager.activityVariable(wfAssignment.getActivityId(), responseVarOutput,
                                apiResponseOutput);
                        LogUtil.info(getClassName(),
                                "Stored Response Body into Workflow Variable: " + responseVarOutput);
                    }
                }
            } catch (Exception e) {
                LogUtil.warn(getClassName(), "Could not set workflow activity variables: " + e.getMessage());
            }
        }

        // 5. Quiet Database Audit Logging to Platform Entity Store (app_fd_sms_api_logs)
        String fullTableName = "app_fd_sms_api_logs";
        String appId = (appDef != null && appDef.getId() != null && !appDef.getId().trim().isEmpty()) ? appDef.getId().trim() : "N/A";
        
        String statusSummary = (responseCode >= 200 && responseCode < 300)
                ? "PASSED (HTTP " + responseCode + ")"
                : "FAILED (HTTP " + responseCode + (apiResponseOutput.isEmpty() ? "" : " - " + apiResponseOutput) + ")";
        
        saveAuditLog(fullTableName, appId, spec.httpMethod, spec.endpoint, spec.payload, statusSummary, responseCode,
                enableDataMasking);

        // 6. Workflow Error Handling (Fail on HTTP Error status)
        boolean failOnError = "true".equalsIgnoreCase(getPropString(mergedProperties, "failOnError"));
        if (failOnError && responseCode >= 400) {
            throw new RuntimeException("API Call Failed with HTTP status (" + responseCode + "): " + statusSummary);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", responseCode);
        result.put("response", statusSummary);
        return result;
    }

    private String escapeJson(String input) {
        if (input == null)
            return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String buildSafeTableName(String appId, String tableName) {
        return "sms_api_logs";
    }

    // High performance audit logger with cached schema validation and sensitive
    // data masking
    private void saveAuditLog(String fullTableName, String appId, String httpMethod, String endpoint, String payload,
            String apiResponseOutput, int responseCode, boolean enableDataMasking) {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

        try (Connection con = ds.getConnection()) {
            if (!VERIFIED_TABLES.containsKey(fullTableName)) {
                synchronized (VERIFIED_TABLES) {
                    if (!VERIFIED_TABLES.containsKey(fullTableName)) {
                        ensureAuditTableExists(con, fullTableName);
                        VERIFIED_TABLES.put(fullTableName, Boolean.TRUE);
                    }
                }
            }

            String targetPayload = enableDataMasking ? maskSensitiveData(payload) : payload;
            String targetResponse = enableDataMasking ? maskSensitiveData(apiResponseOutput) : apiResponseOutput;

            String safePayload = escapeHtml(targetPayload);
            String safeResponse = escapeHtml(targetResponse);

            String insertSql = "INSERT INTO " + fullTableName
                    + " (id, dateCreated, c_app_id, c_method, c_endpoint, c_payload, c_response, c_status_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = con.prepareStatement(insertSql)) {
                pstmt.setString(1, UuidGenerator.getInstance().getUuid());
                pstmt.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
                pstmt.setString(3, appId);
                pstmt.setString(4, httpMethod);
                pstmt.setString(5, endpoint);
                pstmt.setString(6, safePayload);
                pstmt.setString(7, safeResponse);
                pstmt.setInt(8, responseCode);

                pstmt.executeUpdate();
            }

            LogUtil.info(getClassName(), "Successfully logged API audit record to: " + fullTableName);

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Database Error saving audit log to table: " + fullTableName);
        }
    }

    // Single-pass optimized database schema initialization using DatabaseMetaData
    private void ensureAuditTableExists(Connection con, String fullTableName) {
        try {
            DatabaseMetaData dbMeta = con.getMetaData();
            ResultSet rsTable = dbMeta.getTables(null, null, fullTableName, null);
            boolean tableExists = rsTable.next();
            rsTable.close();

            if (!tableExists) {
                // Case fallback check
                rsTable = dbMeta.getTables(null, null, fullTableName.toUpperCase(), null);
                tableExists = rsTable.next();
                rsTable.close();
            }

            if (!tableExists) {
                String createTable = "CREATE TABLE " + fullTableName
                        + " (id VARCHAR(255) NOT NULL, dateCreated DATETIME, dateModified DATETIME, createdBy VARCHAR(255), createdByName VARCHAR(255), modifiedBy VARCHAR(255), modifiedByName VARCHAR(255), PRIMARY KEY (id))";
                try (Statement stmt = con.createStatement()) {
                    stmt.executeUpdate(createTable);
                } catch (Exception e) {
                    LogUtil.warn(getClassName(), "Table creation warning for " + fullTableName + ": " + e.getMessage());
                }
            }

            Set<String> existingColumns = new HashSet<>();
            ResultSet rsCols = dbMeta.getColumns(null, null, fullTableName, null);
            while (rsCols.next()) {
                existingColumns.add(rsCols.getString("COLUMN_NAME").toLowerCase());
            }
            rsCols.close();

            if (existingColumns.isEmpty()) {
                rsCols = dbMeta.getColumns(null, null, fullTableName.toUpperCase(), null);
                while (rsCols.next()) {
                    existingColumns.add(rsCols.getString("COLUMN_NAME").toLowerCase());
                }
                rsCols.close();
            }

            String[] auditColumns = { "c_app_id", "c_method", "c_endpoint", "c_payload", "c_response", "c_status_code" };
            for (String col : auditColumns) {
                if (!existingColumns.contains(col.toLowerCase())) {
                    try (Statement stmt = con.createStatement()) {
                        stmt.executeUpdate("ALTER TABLE " + fullTableName + " ADD COLUMN " + col + " TEXT");
                    } catch (Exception e) {
                        LogUtil.warn(getClassName(),
                                "Column creation warning for " + col + " in " + fullTableName + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error checking audit table schema for " + fullTableName);
        }
    }
}