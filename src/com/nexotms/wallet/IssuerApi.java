package com.nexotms.wallet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * عميل مُصدرنا — العقد الموثق من issuer_core.py:
 * GET /issuer/cards برأس X-Issuer-Token → {"data":[…]} بحقول TP
 * (last4/status/expiration MMYY/account_id — PAN/CVV مكتومان بوعي PCI).
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

    public static Result fetchCards(String host, String token) {
        Result r = new Result();
        HttpURLConnection c = null;
        try {
            URL u = new URL(host.replaceAll("/+$", "") + "/issuer/cards");
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("X-Issuer-Token", token == null ? "" : token);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            r.http = c.getResponseCode();
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    r.http < 400 ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) sb.append(line);
            rd.close();
            JSONObject j = new JSONObject(sb.toString());
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
}
