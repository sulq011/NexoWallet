package com.nexotms.wallet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
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
 * بلاطات بطاقات المُصدر باسم الحامل + البطاقة الافتراضية للدفع
 * + التوكنات والتزويد + تجربة دفع على البطاقة الافتراضية بقرار المحرك
 * + بطاقات الدفع NFC.
 */
public class MainActivity extends Activity {

    private WalletStore store;
    private LinearLayout issuerList, myCardsList;
    private EditText hostEdit, tokenEdit, panEdit, expEdit, cvvEdit, holderEdit;
    private EditText payAmountEdit;
    private EditText activationCodeEdit, actLast4Edit, actExpiryEdit;
    private TextView activationResult;
    private TextView issuerStatus, defCardLabel, payResult;
    private Button readBtn, fillBtn;
    private TextView readerStatus, readerDump;
    private EmvReader.ReadResult lastRead;
    private boolean readerOn = false;
    private NfcAdapter nfc;
    private final Handler readHandler = new Handler();
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

        // ===== 1ب) تفعيل بطاقة فيزيائية (نمط برق — لمسة NFC) =====
        root.addView(section("💳 تفعيل بطاقة فيزيائية"));

        activationCodeEdit = new EditText(this);
        activationCodeEdit.setHint("رمز التفعيل (من وسم البطاقة أو المُصدر)");
        activationCodeEdit.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(activationCodeEdit);

        actLast4Edit = new EditText(this);
        actLast4Edit.setHint("آخر 4 أرقام من البطاقة");
        actLast4Edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(actLast4Edit);

        actExpiryEdit = new EditText(this);
        actExpiryEdit.setHint("الانتهاء MMYY (مثال 1130)");
        actExpiryEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(actExpiryEdit);

        Button actBtn = new Button(this);
        actBtn.setText("تفعيل البطاقة الفيزيائية");
        root.addView(actBtn);
        actBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activatePhysicalCard();
            }
        });

        activationResult = new TextView(this);
        activationResult.setTextSize(13);
        activationResult.setPadding(0, px(6), 0, px(6));
        root.addView(activationResult);

        TextView actNote = new TextView(this);
        actNote.setTextSize(11);
        actNote.setPadding(0, px(6), 0, 0);
        actNote.setText("لمس بطاقة تحمل وسم nexowallet://activatePhysical?id=… يفتح هذا القسم ويملأ الرمز تلقائيًا — مثل بطاقات برق تمامًا.");
        root.addView(actNote);

        // ===== 2) تجربة دفع على البطاقة الافتراضية =====
        root.addView(section("🧾 تجربة دفع — البطاقة الافتراضية"));

        defCardLabel = new TextView(this);
        defCardLabel.setTextSize(13);
        root.addView(defCardLabel);

        payAmountEdit = new EditText(this);
        payAmountEdit.setHint("المبلغ (مثال 25.00)");
        payAmountEdit.setInputType(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(payAmountEdit);

        Button payBtn = new Button(this);
        payBtn.setText("تنفيذ تجربة الدفع");
        root.addView(payBtn);
        payBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                payTest();
            }
        });

        payResult = new TextView(this);
        payResult.setTextSize(13);
        payResult.setPadding(0, px(6), 0, px(6));
        root.addView(payResult);

        // ===== 2ب) قراءة بطاقة NFC (قارئ المعمل) =====
        root.addView(section("📖 قراءة بطاقة NFC (قارئ)"));

        readBtn = new Button(this);
        readBtn.setText("قراءة بطاقة — قرّبها من ظهر الجهاز");
        root.addView(readBtn);
        readBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startReader();
            }
        });

        readerStatus = new TextView(this);
        readerStatus.setTextSize(12);
        readerStatus.setPadding(0, px(4), 0, px(4));
        root.addView(readerStatus);

        readerDump = new TextView(this);
        readerDump.setTextSize(10);
        readerDump.setPadding(px(6), px(6), px(6), px(6));
        readerDump.setText("");
        root.addView(readerDump);

        fillBtn = new Button(this);
        fillBtn.setText("تعبئة نموذج الإضافة من القراءة");
        fillBtn.setEnabled(false);
        root.addView(fillBtn);
        fillBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fillFormFromRead();
            }
        });

        TextView readNote = new TextView(this);
        readNote.setTextSize(11);
        readNote.setPadding(0, px(6), 0, 0);
        readNote.setText("القراءة محلية بحتة — لا يُرسل أي بايت لأي سيرفر. بطاقاتك الملغاة يجيب رقاقها محليًا (الإلغاء عند الشبكة لا عند الشريحة) — PAN/الصلاحية/track2 تُستخرج بEMV كامل وتعبّئ نموذج الإضافة لتدخل حلقة معملنا (HCE ← POS المعمل).");
        root.addView(readNote);


        // ===== 3) بطاقاتي للدفع NFC =====
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

        updateDefCardLabel();
        renderMyCards();
        handleActivationLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleActivationLink(i);
    }

    /** استقبال nexowallet://activatePhysical?id=CODE — تعبئة الرمز مباشرة */
    private void handleActivationLink(Intent i) {
        if (i == null || !Intent.ACTION_VIEW.equals(i.getAction())) return;
        Uri d = i.getData();
        if (d == null) return;
        String sch = d.getScheme() == null ? "" : d.getScheme().toLowerCase();
        String hst = d.getHost() == null ? "" : d.getHost().toLowerCase();
        if (!("nexowallet".equals(sch) && "activatephysical".equals(hst))) return;
        String code = d.getQueryParameter("id");
        if (code == null || code.trim().isEmpty()) {
            Toast.makeText(this, "رابط تفعيل بلا رمز — أدخل الرمز يدويًا",
                    Toast.LENGTH_LONG).show();
            return;
        }
        activationCodeEdit.setText(code.trim());
        actLast4Edit.requestFocus();
        Toast.makeText(this, "وصل رمز تفعيل من وسم البطاقة ✓ — أدخل آخر4 والانتهاء",
                Toast.LENGTH_LONG).show();
    }

    /** تفعيل بطاقة فيزيائية عبر نقطة المُصدر المُثبتة */
    private void activatePhysicalCard() {
        final String host = hostEdit.getText().toString().trim();
        final String token = tokenEdit.getText().toString().trim();
        final String code = activationCodeEdit.getText().toString().trim();
        final String last4 = actLast4Edit.getText().toString().trim();
        final String expiry = actExpiryEdit.getText().toString().trim();
        if (code.isEmpty() || last4.length() != 4) {
            Toast.makeText(this, "أدخل رمز التفعيل وآخر 4 أرقام", Toast.LENGTH_SHORT).show();
            return;
        }
        store.setHost(host);
        store.setToken(token);
        activationResult.setText("… جارٍ تفعيل البطاقة عبر المُصدر");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final IssuerApi.ActivationResult ar =
                        IssuerApi.activatePhysical(host, token, code, last4, expiry);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ar.http == 200) {
                            activationResult.setText("✓ البطاقة •••• " + ar.last4
                                    + " مفعّلة — الحالة " + ar.status
                                    + (ar.pinIsSet ? " — PIN مضبوط" : "")
                                    + "\nفعّلت في: " + ar.activatedAt);
                            Toast.makeText(MainActivity.this,
                                    "تفعيل ناجح ✓", Toast.LENGTH_LONG).show();
                            fetchIssuerCards();
                        } else if (ar.http == 423) {
                            activationResult.setText("✗ البطاقة مقفلة بعد 3 محاولات فاشلة (423)");
                        } else if (ar.http == 409) {
                            activationResult.setText("البطاقة مفعّلة مسبقًا (409)\n"
                                    + ar.activatedAt);
                        } else if (ar.http == 403) {
                            activationResult.setText("✗ بيانات غير مطابقة (403)"
                                    + " — محاولات: " + ar.attempts
                                    + (ar.locked ? " — مقفلة" : ""));
                        } else {
                            activationResult.setText("HTTP " + ar.http
                                    + (ar.error.isEmpty() ? "" : " — " + ar.error));
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopReader();
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
        updateDefCardLabel();
    }

    /** سطر البطاقة الافتراضية في قسم تجربة الدفع */
    private void updateDefCardLabel() {
        String defId = store.getDefaultIssuerCard();
        for (IssuerApi.IssuerCard ic : issuerCards) {
            if (ic.id.equals(defId)) {
                String holder = (ic.holderName == null || ic.holderName.isEmpty())
                        ? "" : (" — " + ic.holderName);
                defCardLabel.setText("البطاقة الافتراضية: •••• " + ic.last4 + holder);
                return;
            }
        }
        defCardLabel.setText("لم تُحدد بطاقة افتراضية — اخترها من المحفظة أعلاه");
    }

    /** تجربة دفع: تمريرة عبر المُصدر ثم انتظار قرار المحرك في السجل */
    private void payTest() {
        final String host = hostEdit.getText().toString().trim();
        final String token = tokenEdit.getText().toString().trim();
        final String defId = store.getDefaultIssuerCard();
        final IssuerApi.IssuerCard card = findCard(defId);
        if (card == null) {
            Toast.makeText(this, "اختر بطاقة افتراضية للدفع أولًا", Toast.LENGTH_SHORT).show();
            return;
        }
        final String amount = payAmountEdit.getText().toString().trim();
        if (!amount.matches("\\d+(\\.\\d{1,2})?")) {
            Toast.makeText(this, "أدخل مبلغًا صحيحًا مثل 25.00", Toast.LENGTH_SHORT).show();
            return;
        }
        payResult.setText("… تمريرة " + amount + " على •••• " + card.last4
                + " — بانتظار قرار المحرك");
        new Thread(new Runnable() {
            @Override
            public void run() {
                int before = countDecisions(host, token, defId);
                final int http = IssuerApi.simulatePurchase(host, token, defId, amount);
                IssuerApi.LogEntry decision = null;
                if (http == 202) {
                    for (int i = 0; i < 15; i++) {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                        IssuerApi.LogResult lr = IssuerApi.fetchLog(host, token);
                        int seen = 0;
                        for (int k = 0; k < lr.entries.size(); k++) {
                            if (defId.equals(lr.entries.get(k).cardId)) seen++;
                        }
                        if (seen > before) {
                            for (int k = lr.entries.size() - 1; k >= 0; k--) {
                                IssuerApi.LogEntry e = lr.entries.get(k);
                                if (defId.equals(e.cardId)
                                        && amount.equals(e.amount)) {
                                    decision = e;
                                    break;
                                }
                            }
                            if (decision != null) break;
                        }
                    }
                }
                final IssuerApi.LogEntry dec = decision;
                final int code = http;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (code != 202) {
                            payResult.setText("فشل إرسال التمريرة (HTTP " + code + ") — تحقق من التوكن");
                            return;
                        }
                        if (dec == null) {
                            payResult.setText("التمريرة قُبلت (202) لكن القرار لم يصل خلال 30ث — راجع نشاط المُصدر");
                            return;
                        }
                        boolean ok = "approved".equals(dec.decision);
                        payResult.setText((ok ? "✓ موافقة" : "✗ رفض")
                                + " — " + dec.reason + "\nالبطاقة: •••• " + card.last4
                                + "  المبلغ: " + amount);
                    }
                });
            }
        }).start();
    }

    private IssuerApi.IssuerCard findCard(String id) {
        for (IssuerApi.IssuerCard ic : issuerCards) {
            if (ic.id.equals(id)) return ic;
        }
        return null;
    }

    private int countDecisions(String host, String token, String cardId) {
        IssuerApi.LogResult lr = IssuerApi.fetchLog(host, token);
        int n = 0;
        for (int i = 0; i < lr.entries.size(); i++) {
            if (cardId.equals(lr.entries.get(i).cardId)) n++;
        }
        return n;
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

    // ===== القارئ: NFC reader mode + دورة EMV كاملة =====
    private final NfcAdapter.ReaderCallback readCb = new NfcAdapter.ReaderCallback() {
        @Override
        public void onTagDiscovered(final Tag tag) {
            // سطر خام فوري قبل أي حوار — هذا يثبت أن الوسم وصل أصلًا
            StringBuilder hdr = new StringBuilder();
            try {
                hdr.append("== وسم جديد ==\nUID: ");
                StringBuilder ub = new StringBuilder();
                for (byte b : tag.getId()) ub.append(String.format("%02X", b));
                hdr.append(ub).append("\nالتقنيات: ");
                for (String t : tag.getTechList()) hdr.append(t).append(" ");
            } catch (Exception e) {
                hdr.append("خطأ وسم: ").append(e);
            }
            final String head = hdr.toString();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    readerDump.setText(head);
                }
            });
            final IsoDep iso = IsoDep.get(tag);
            if (iso == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        readerStatus.setText("الوسم ليس ISO-DEP — راجع قائمة التقنيات أعلاه");
                    }
                });
                return;
            }
            try {
                iso.connect();
                iso.setTimeout(15000);
            } catch (Exception e) {
                final String es = "فشل connect: " + e;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        readerStatus.setText(es);
                    }
                });
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    readerStatus.setText("… حوار EMV جارٍ (لا تُبعد البطاقة)");
                }
            });
            // القراءة في خيط مستقل — الواجهة لا تتجمد ولا يُقطع الحوار
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final EmvReader.ReadResult res = EmvReader.read(iso);
                    try {
                        iso.close();
                    } catch (Exception ignored) {
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            lastRead = res;
                            readerDump.setText(head + "\n" + res.log.toString());
                            if (res.ok) {
                                String mm = res.expiry.length() == 4
                                        ? res.expiry.substring(2) + "/" + res.expiry.substring(0, 2)
                                        : res.expiry;
                                readerStatus.setText("✓ " + res.aidName
                                        + " — بطاقة •••• "
                                        + (res.pan.length() >= 4 ? res.pan.substring(res.pan.length() - 4) : "?")
                                        + " — انتهاء " + mm
                                        + (res.atc.isEmpty() ? "" : " — ATC " + res.atc)
                                        + (res.tech.isEmpty() ? "" : " — ATS " + res.tech));
                                fillBtn.setEnabled(true);
                                Toast.makeText(MainActivity.this,
                                        "قُرئت البطاقة محليًا — جاهزة للتعبئة",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                readerStatus.setText("خطأ القراءة: " + res.error
                                        + " — راجع السجل الخام أدناه");
                                fillBtn.setEnabled(false);
                            }
                        }
                    });
                }
            }).start();
        }
    };

    private final Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            stopReader();
            readerStatus.setText("انتهت مهلة الدقيقتين — اضغط الإيقاف ثم القراءة مجددًا");
        }
    };

    private void startReader() {
        if (readerOn) { // زر تبديل: إيقاف فوري
            stopReader();
            readerStatus.setText("أُوقف القارئ يدويًا");
            return;
        }
        nfc = nfc == null ? NfcAdapter.getDefaultAdapter(this) : nfc;
        if (nfc == null) {
            readerStatus.setText("هذا الجهاز بلا NFC");
            return;
        }
        readerDump.setText("");
        lastRead = null;
        fillBtn.setEnabled(false);
        nfc.enableReaderMode(this, readCb, NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        readerOn = true;
        readBtn.setText("إيقاف القارئ");
        readerStatus.setText("قرّب البطاقة من ظهر الجهاز… (نافذة دقيقتين، ولا يتوقف بعد أول بطاقة)");
        readHandler.postDelayed(stopRunnable, 120000);
    }

    private void stopReader() {
        readHandler.removeCallbacks(stopRunnable);
        readerOn = false;
        readBtn.setText("قراءة بطاقة — قرّبها من ظهر الجهاز");
        if (nfc != null) {
            try {
                nfc.disableReaderMode(this);
            } catch (Exception ignored) {
            }
        }
    }

    private void fillFormFromRead() {
        if (lastRead == null || !lastRead.ok) {
            Toast.makeText(this, "لا قراءة صالحة", Toast.LENGTH_SHORT).show();
            return;
        }
        panEdit.setText(lastRead.pan);
        if (lastRead.expiry.length() == 4) {
            expEdit.setText(lastRead.expiry.substring(2) + "/" + lastRead.expiry.substring(0, 2));
        }
        cvvEdit.requestFocus();
        Toast.makeText(this, "اكتملت التعبئة — أدخل CVV واسم الحامل ثم اضغط إضافة البطاقة",
                Toast.LENGTH_LONG).show();
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
