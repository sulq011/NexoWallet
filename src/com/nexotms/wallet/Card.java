package com.nexotms.wallet;

import org.json.JSONException;
import org.json.JSONObject;

/** بطاقة محلية قابلة للدفع NFC — بيانات كاملة (PAN/انتهاء/CVV/الحامل). */
public class Card {
    public String pan;
    public String holder;
    public String expiry; // MM/YY
    public String cvv;
    public String label;

    public Card() {}

    public Card(String pan, String holder, String expiry, String cvv, String label) {
        this.pan = pan;
        this.holder = holder;
        this.expiry = expiry;
        this.cvv = cvv;
        this.label = label;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("pan", pan);
        o.put("holder", holder);
        o.put("expiry", expiry);
        o.put("cvv", cvv);
        o.put("label", label);
        return o;
    }

    public static Card fromJson(JSONObject o) {
        Card c = new Card();
        c.pan = o.optString("pan", "");
        c.holder = o.optString("holder", "");
        c.expiry = o.optString("expiry", "");
        c.cvv = o.optString("cvv", "");
        c.label = o.optString("label", "");
        return c;
    }

    public String last4() {
        return pan == null || pan.length() < 4 ? pan : pan.substring(pan.length() - 4);
    }
}
