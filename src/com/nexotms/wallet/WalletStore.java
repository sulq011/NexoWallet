package com.nexotms.wallet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * تخزين محلي للمحفظة: بطاقات الدفع NFC + إعدادات مُصدرنا.
 * التوكن يُعرض نصًّا صريحًا في الواجهة (طلب المالك) ويُخزَّن في prefs المعمل.
 */
public class WalletStore {
    public static final String DEFAULT_HOST = "https://159.195.246.207.nip.io";
    private final SharedPreferences prefs;

    public WalletStore(Context ctx) {
        prefs = ctx.getSharedPreferences("nexowallet", Context.MODE_PRIVATE);
    }

    public List<Card> load() {
        List<Card> out = new ArrayList<Card>();
        try {
            JSONArray a = new JSONArray(prefs.getString("cards", "[]"));
            for (int i = 0; i < a.length(); i++) {
                out.add(Card.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void save(List<Card> cards) {
        JSONArray a = new JSONArray();
        for (Card c : cards) {
            try {
                a.put(c.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString("cards", a.toString()).apply();
    }

    public String getSelectedPan() {
        return prefs.getString("sel", "");
    }

    public void setSelectedPan(String pan) {
        prefs.edit().putString("sel", pan).apply();
    }

    public Card byPan(List<Card> cards, String pan) {
        for (Card c : cards) {
            if (c.pan != null && c.pan.equals(pan)) return c;
        }
        return null;
    }

    public String getHost() {
        return prefs.getString("host", DEFAULT_HOST);
    }

    public void setHost(String host) {
        prefs.edit().putString("host", host).apply();
    }

    public String getToken() {
        return prefs.getString("token", "");
    }

    public void setToken(String t) {
        prefs.edit().putString("token", t).apply();
    }

    /** البطاقة المُصدَرة المختارة كافتراضية للدفع */
    public String getDefaultIssuerCard() {
        return prefs.getString("def_issuer", "");
    }

    public void setDefaultIssuerCard(String cardId) {
        prefs.edit().putString("def_issuer", cardId).apply();
    }
}
