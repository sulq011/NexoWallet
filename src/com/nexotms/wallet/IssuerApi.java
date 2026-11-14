package com.nexotms.wallet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * عميل مُصدرنا — العقد الموثق من issuer_core.py:
 * GET  /issuer/cards                  (X-Issuer-Token) → {"data":[…]}
 * GET  /issuer/cards/{id}/wallet_tokens → مصفوفة عارية (عقد موثق حيًا)
 * POST /issuer/cards/{id}/provision_wallet_token {"requestor":…} → كائن التوكن.
 * (PAN/CVV مكتومان عند المُصدر بوعي PCI — التوكنة هي الطريق الإنتاجي للبطاقة).
 */
public class IssuerApi {

    public static class IssuerCard {
        public String id = "";
        public String last4 = "";
        public String status = "";
        public String expiration = "";
        public String accountId = "";

        public String display() {
            String exp = expiration == null ? "" : expiration;
            String mm = exp.length() == 4 ? exp.substring(0, 2) + "/" + exp.substring(2) : exp;
            return "بطاقة •••• " + last4 + " — " + status + " — انتهاء " + mm
                    + "\nالحساب: " + accountId;
        }
    }

    public static class Result {
        public int http;
        public String error;
        public List<IssuerCard> cards = new ArrayList<IssuerCard>();
    }

    /** توكنات محفظة بطاقة واحدة: أسطر جاهزة للعرض */
    public static class TokensResult {
        public int http;
        public String error;
        public List<String> lines = new ArrayList<String>();
    }

    /** نتيجة التزويد عبر المُصدر */
    public static class ProvisionResult {
        public int http;
        public String error;
        public String tokenId = "";
        public String requestor = "";
    }

    private static String _read(HttpURLConnection c, int code) throws Exception {
        BufferedReader rd = new BufferedReader(new InputStreamReader(
                code < 400 ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) sb.append(line);
        rd.close();
        return sb.toString();
    }

    private static String _host(String host) {
        return host.replaceAll("/+$", "");
    }

    public static Result fetchCards(String host, String token) {
        Result r = new Result();
        HttpURLConnection c = null;
        try {
            URL u = new URL(_host(host) + "/issuer/cards");
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("X-Issuer-Token", token == null ? "" : token);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            r.http = c.getResponseCode();
            JSONObject j = new JSONObject(_read(c, r.http));
            JSONArray data = j.optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject o = data.getJSONObject(i);
                    IssuerCard ic = new IssuerCard();
                    ic.id = o.optString("id", "");
                    ic.last4 = o.optString("last4", "");
                    ic.status = o.optString("status", "");
                    ic.expiration = o.optString("expiration", "");
                    ic.accountId = o.optString("account_id", "");
                    r.cards.add(ic);
                }
            }
        } catch (Exception e) {
            r.error = String.valueOf(e);
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    /** GET /issuer/cards/{id}/wallet_tokens — مصفوفة عارية */
    public static TokensResult fetchTokens(String host, String token, String cardId) {
        TokensResult r = new TokensResult();
        HttpURLConnection c = null;
        try {
            URL u = new URL(_host(host) + "/issuer/cards/" + cardId + "/wallet_tokens");
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("X-Issuer-Token", token == null ? "" : token);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            r.http = c.getResponseCode();
            JSONArray a = new JSONArray(_read(c, r.http));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                JSONObject tsp = o.optJSONObject("token_service_provider");
                String req = tsp == null ? "" : tsp.optString("token_requestor_name", "");
                r.lines.add(req + " — " + o.optString("id", ""));
            }
        } catch (Exception e) {
            r.error = String.valueOf(e);
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    /** POST /issuer/cards/{id}/provision_wallet_token — المُصدر يبدأ التوكنة عند TP */
    public static ProvisionResult provision(String host, String token,
                                            String cardId, String requestor) {
        ProvisionResult r = new ProvisionResult();
        HttpURLConnection c = null;
        try {
            URL u = new URL(_host(host) + "/issuer/cards/" + cardId
                    + "/provision_wallet_token");
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-Issuer-Token", token == null ? "" : token);
            c.setDoOutput(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(45000); // المُصدر يستطلع TP حتى 30ث حتى يظهر التوكن
            byte[] payload = new JSONObject().put("requestor", requestor)
                    .toString().getBytes("UTF-8");
            OutputStream os = c.getOutputStream();
            os.write(payload);
            os.close();
            r.http = c.getResponseCode();
            String body = _read(c, r.http);
            if (r.http == 200) {
                JSONObject o = new JSONObject(body);
                r.tokenId = o.optString("id", "");
                JSONObject tsp = o.optJSONObject("token_service_provider");
                r.requestor = tsp == null ? "" : tsp.optString("token_requestor_name", "");
            } else {
                r.error = body.length() > 200 ? body.substring(0, 200) : body;
            }
        } catch (Exception e) {
            r.error = String.valueOf(e);
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }
}
