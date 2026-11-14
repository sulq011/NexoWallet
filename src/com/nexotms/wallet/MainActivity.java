package com.nexotms.wallet;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * محفظة nexo-tms — محفظة حقيقية:
 * بطاقات المُصدر كبلاطات بنكية (اسم الحامل + آخر4 + الحالة) مع التوكنات
 * والتزويد وتحديد البطاقة الافتراضية للدفع — إضافة إلى بطاقات الدفع NFC.
 */
public class MainActivity extends Activity {

    private WalletStore store;
    private LinearLayout issuerList, myCardsList;
    private EditText hostEdit, tokenEdit, panEdit, expEdit, cvvEdit, holderEdit;
    private TextView issuerStatus;
    private List<IssuerApi.IssuerCard> issuerCards = new ArrayList<IssuerApi.IssuerCard>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new WalletStore(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(16), px(24), px(16), px(24));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("💳 محفظة nexo-tms");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        // ===== 1) محفظتي — بطاقات المُصدر =====
        root.addView(section("🏦 محفظتي — بطاقاتي من المُصدر"));

        hostEdit = new EditText(this);
        hostEdit.setHint("المضيف (https://…)");
        hostEdit.setText(store.getHost());
        root.addView(hostEdit);

        tokenEdit = new EditText(this);
        tokenEdit.setHint("توكن المُصدر (X-Issuer-Token)");
        tokenEdit.setText(store.getToken());
        tokenEdit.setInputType(InputType.TYPE_CLASS_TEXT); // نص صريح — بطلب المالك
        root.addView(tokenEdit);

        Button loadBtn = new Button(this);
        loadBtn.setText("تحميل محفظتي");
        root.addView(loadBtn);
        loadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchIssuerCards();
            }
        });

        issuerStatus = new TextView(this);
        issuerStatus.setTextSize(12);
        issuerStatus.setPadding(0, px(4), 0, px(4));
        root.addView(issuerStatus);

        issuerList = new LinearLayout(this);
        issuerList.setOrientation(LinearLayout.VERTICAL);
        root.addView(issuerList);

        // ===== 2) بطاقاتي للدفع NFC =====
        root.addView(section("📲 بطاقاتي للدفع NFC"));

        myCardsList = new LinearLayout(this);
        myCardsList.setOrientation(LinearLayout.VERTICAL);
        root.addView(myCardsList);

        panEdit = new EditText(this);
        panEdit.setHint("رقم البطاقة (PAN)");
        panEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(panEdit);

        LinearLayout expRow = new LinearLayout(this);
        expRow.setOrientation(LinearLayout.HORIZONTAL);
        expEdit = new EditText(this);
        expEdit.setHint("MM/YY");
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        expEdit.setLayoutParams(p1);
        cvvEdit = new EditText(this);
        cvvEdit.setHint("CVV");
        cvvEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cvvEdit.setLayoutParams(p2);
        expRow.addView(expEdit);
        expRow.addView(cvvEdit);
        root.addView(expRow);

        holderEdit = new EditText(this);
        holderEdit.setHint("اسم الحامل");
        root.addView(holderEdit);

        Button addBtn = new Button(this);
        addBtn.setText("إضافة البطاقة");
        root.addView(addBtn);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addCard();
            }
        });

        TextView nfcHint = new TextView(this);
        nfcHint.setTextSize(11);
        nfcHint.setPadding(0, px(10), 0, 0);
        nfcHint.setText("بطاقات المُصدر توكنية (لا تحمل PAN بوعي PCI) — الدفع التلامسي الفعلي عند القارئ من هذا القسم ببطاقة ببيانات كاملة.\n"
                + "تفعيل الدفع NFC: إعدادات الجهاز ← NFC ← Tap & pay ← اختر «محفظة nexo-tms».");
        root.addView(nfcHint);

        renderMyCards();
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, px(20), 0, px(8));
        return t;
    }

    private int px(int d) {
        return (int) (d * getResources().getDisplayMetrics().density);
    }

    private void fetchIssuerCards() {
        final String host = hostEdit.getText().toString().trim();
        final String token = tokenEdit.getText().toString().trim();
        store.setHost(host);
        store.setToken(token);
        issuerStatus.setText("… جارٍ جلب محفظتك من المُصدر");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final IssuerApi.Result r = IssuerApi.fetchCards(host, token);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (r.error != null) {
                            issuerStatus.setText("خطأ: " + r.error);
                            return;
                        }
                        if (r.http != 200) {
                            issuerStatus.setText("HTTP " + r.http + " — تحقق من التوكن");
                            return;
                        }
                        issuerCards = r.cards;
                        renderIssuerCards();
                    }
                });
            }
        }).start();
    }

    /** رسم المحفظة: بلاطة بنكية لكل بطاقة مُصدرة */
    private void renderIssuerCards() {
        issuerList.removeAllViews();
        if (issuerCards.isEmpty()) {
            issuerStatus.setText("لا بطاقات عند المُصدر");
            return;
        }
        String defId = store.getDefaultIssuerCard();
        issuerStatus.setText("✓ محفظتك: " + issuerCards.size() + " بطاقة");
        for (final IssuerApi.IssuerCard ic : issuerCards) {
            boolean isDefault = ic.id.equals(defId);

            // ==== البلاطة ====
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    isDefault
                        ? new int[]{0xFF0E5E3F, 0xFF0A5C74}
                        : new int[]{0xFF1B2A6B, 0xFF0E7C86});
            float r = px(14);
            bg.setCornerRadii(new float[]{r, r, r, r, r, r, r, r});
            tile.setBackground(bg);
            tile.setPadding(px(18), px(14), px(18), px(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = px(8);
            lp.topMargin = px(4);
            tile.setLayoutParams(lp);

            String holder = (ic.holderName == null || ic.holderName.isEmpty())
                    ? "بطاقة من مُصدرنا" : ic.holderName;
            TextView name = new TextView(this);
            name.setText(holder);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(15);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            tile.addView(name);

            TextView num = new TextView(this);
            num.setText("•••• " + ic.last4);
            num.setTextColor(0xFFFFFFFF);
            num.setTextSize(24);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            tile.addView(num);

            TextView meta = new TextView(this);
            String exp = ic.expiration == null ? "" : ic.expiration;
            String mm = exp.length() == 4 ? exp.substring(0, 2) + "/" + exp.substring(2) : exp;
            meta.setText("انتهاء " + mm + "  —  " + ic.status
                    + (isDefault ? "  ✓ الافتراضية للدفع" : ""));
            meta.setTextColor(0xCCFFFFFF);
            meta.setTextSize(12);
            tile.addView(meta);

            TextView acct = new TextView(this);
            acct.setText("الحساب: " + ic.accountId);
            acct.setTextColor(0x99FFFFFF);
            acct.setTextSize(10);
            tile.addView(acct);

            issuerList.addView(tile);

            // ==== توكنات المحفظة ====
            final TextView tokView = new TextView(this);
            tokView.setTextSize(12);
            tokView.setPadding(px(10), px(4), px(10), px(2));
            tokView.setText("توكنات المحفظة: …");
            issuerList.addView(tokView);
            loadTokens(ic.id, tokView);

            // ==== اختيار البطاقة الافتراضية للدفع ====
            Button defBtn = new Button(this);
            defBtn.setTextSize(12);
            defBtn.setText(isDefault
                    ? "✓ البطاقة الافتراضية للدفع"
                    : "استخدامها كبطاقة افتراضية للدفع");
            defBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    store.setDefaultIssuerCard(ic.id);
                    renderIssuerCards();
                }
            });
            issuerList.addView(defBtn);

            // ==== التزويد (توكنة إلى محفظة رقمية) ====
            Button provBtn = new Button(this);
            provBtn.setTextSize(12);
            provBtn.setText("توكنة إلى محفظة رقمية (Google Pay)");
            provBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    provisionCard(ic.id, provBtn, tokView);
                }
            });
            issuerList.addView(provBtn);
        }
    }

    /** جلب توكنات بطاقة وعرضها في السطر المخصص لها */
    private void loadTokens(final String cardId, final TextView view) {
        final String host = hostEdit.getText().toString().trim();
        final String token = tokenEdit.getText().toString().trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final IssuerApi.TokensResult tr = IssuerApi.fetchTokens(host, token, cardId);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (tr.error != null) {
                            view.setText("توكنات المحفظة: خطأ " + tr.error);
                            return;
                        }
                        if (tr.lines.isEmpty()) {
                            view.setText("توكنات المحفظة: لا توكنات بعد");
                            return;
                        }
                        StringBuilder sb = new StringBuilder("توكنات المحفظة:");
                        for (String s : tr.lines) sb.append("\n• ").append(s);
                        view.setText(sb.toString());
                    }
                });
            }
        }).start();
    }

    /** التزويد عبر المُصدر: POST provision_wallet_token ثم تحديث السطر */
    private void provisionCard(final String cardId, final Button btn, final TextView view) {
        final String host = hostEdit.getText().toString().trim();
        final String token = tokenEdit.getText().toString().trim();
        btn.setEnabled(false);
        view.setText("… جارٍ التزويد عبر المُصدر (حتى 30ث)");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final IssuerApi.ProvisionResult pr =
                        IssuerApi.provision(host, token, cardId, "android_pay");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btn.setEnabled(true);
                        if (pr.error != null) {
                            view.setText("خطأ التزويد: " + pr.error);
                        } else if (pr.http != 200) {
                            view.setText("HTTP " + pr.http + " — " + pr.error);
                        } else {
                            view.setText("✓ توكن جديد: " + pr.requestor + " — " + pr.tokenId);
                            Toast.makeText(MainActivity.this,
                                    "تمت التوكنة: " + pr.tokenId,
                                    Toast.LENGTH_LONG).show();
                        }
                        loadTokens(cardId, view);
                    }
                });
            }
        }).start();
    }

    private void addCard() {
        String pan = panEdit.getText().toString().trim();
        String exp = expEdit.getText().toString().trim();
        String cvv = cvvEdit.getText().toString().trim();
        String holder = holderEdit.getText().toString().trim();
        if (pan.length() < 12 || !exp.matches("\\d{2}/\\d{2}")) {
            Toast.makeText(this, "أدخل PAN صحيحًا وتاريخ MM/YY", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Card> cards = store.load();
        cards.add(new Card(pan, holder.isEmpty() ? "SAD AM" : holder, exp, cvv, "بطاقتي"));
        store.save(cards);
        if (store.getSelectedPan().isEmpty()) store.setSelectedPan(pan);
        panEdit.setText("");
        expEdit.setText("");
        cvvEdit.setText("");
        holderEdit.setText("");
        renderMyCards();
    }

    private void renderMyCards() {
        myCardsList.removeAllViews();
        final List<Card> cards = store.load();
        if (cards.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("لا بطاقات بعد — أضف بطاقتك بالأسفل");
            tv.setTextSize(12);
            myCardsList.addView(tv);
            return;
        }
        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.VERTICAL);
        for (final Card c : cards) {
            RadioButton rb = new RadioButton(this);
            rb.setText("•••• " + c.last4() + "  (" + c.expiry + ")");
            rb.setTextSize(14);
            if (c.pan.equals(store.getSelectedPan())) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int idx = rg.indexOfChild(rg.findViewById(checkedId));
                if (idx >= 0 && idx < cards.size()) {
                    store.setSelectedPan(cards.get(idx).pan);
                    Toast.makeText(MainActivity.this,
                            "بطاقة الدفع NFC الآن: •••• " + cards.get(idx).last4(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        myCardsList.addView(rg);

        Button delBtn = new Button(this);
        delBtn.setText("حذف المختارة");
        delBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Card> cs = store.load();
                String sel = store.getSelectedPan();
                for (int i = 0; i < cs.size(); i++) {
                    if (cs.get(i).pan.equals(sel)) {
                        cs.remove(i);
                        break;
                    }
                }
                store.save(cs);
                if (!cs.isEmpty()) store.setSelectedPan(cs.get(0).pan);
                else store.setSelectedPan("");
                renderMyCards();
            }
        });
        myCardsList.addView(delBtn);
    }
}
