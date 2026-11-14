package com.nexotms.wallet;

import android.app.Activity;
import android.graphics.Typeface;
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

import java.util.List;

/**
 * محفظة nexo-tms — بطاقات مُصدرنا (عرض حي + توكنات المحفظة + التزويد عبر المُصدر)
 * + بطاقات الدفع NFC. واجهة برمجية كاملة بلا موارد تخطيط — مثل بقية تطبيقات المعمل.
 */
public class MainActivity extends Activity {

    private WalletStore store;
    private LinearLayout issuerList, myCardsList;
    private EditText hostEdit, tokenEdit, panEdit, expEdit, cvvEdit, holderEdit;
    private TextView issuerStatus;

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

        // ===== 1) مُصدرنا =====
        root.addView(section("🏦 بطاقات مُصدرنا (السيرفر الحالي)"));

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
        loadBtn.setText("تحميل البطاقات من المُصدر");
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

        TextView note = new TextView(this);
        note.setTextSize(11);
        note.setPadding(0, px(8), 0, 0);
        note.setText("حد الصدق: PAN/CVV مكتومان عند مُصدرنا (وعي PCI) — أدخل بيانات بطاقتك كما تظهر في لوحة المُصدر.\n"
                + "بطاقة بلا BIN مرخّص: أي POS حقيقي يقرأها EMV كاملة ثم يأتي الرفض من الشبكة الحقيقية.");
        root.addView(note);

        TextView dwtNote = new TextView(this);
        dwtNote.setTextSize(11);
        dwtNote.setPadding(0, px(6), 0, 0);
        dwtNote.setText("التوكنة (DPAN): المُصدر يبدأها عند الشبكة — البطاقة تدخل المحفظة الرقمية بلا كشف PAN أبدًا.");
        root.addView(dwtNote);

        TextView nfcHint = new TextView(this);
        nfcHint.setTextSize(11);
        nfcHint.setPadding(0, px(10), 0, 0);
        nfcHint.setText("تفعيل الدفع NFC: إعدادات الجهاز ← NFC ← Tap & pay ← اختر «محفظة nexo-tms»، ثم قرّب الجهاز من القارئ.");
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
        issuerStatus.setText("… جارٍ الجلب من المُصدر");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final IssuerApi.Result r = IssuerApi.fetchCards(host, token);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        issuerList.removeAllViews();
                        if (r.error != null) {
                            issuerStatus.setText("خطأ: " + r.error);
                            return;
                        }
                        if (r.http != 200) {
                            issuerStatus.setText("HTTP " + r.http + " — تحقق من التوكن");
                            return;
                        }
                        issuerStatus.setText("✓ " + r.cards.size() + " بطاقة من المُصدر");
                        for (final IssuerApi.IssuerCard ic : r.cards) {
                            TextView tv = new TextView(MainActivity.this);
                            tv.setText(ic.display());
                            tv.setTextSize(13);
                            tv.setPadding(px(10), px(10), px(10), 0);
                            issuerList.addView(tv);

                            // توكنات المحفظة لهذه البطاقة (مسار DPAN)
                            final TextView tokView = new TextView(MainActivity.this);
                            tokView.setTextSize(12);
                            tokView.setPadding(px(10), px(4), px(10), px(4));
                            tokView.setText("توكنات المحفظة: …");
                            issuerList.addView(tokView);
                            loadTokens(ic.id, tokView);

                            final Button provBtn = new Button(MainActivity.this);
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
                });
            }
        }).start();
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
