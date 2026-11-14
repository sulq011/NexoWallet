package com.nexotms.wallet;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.util.List;

/**
 * خدمة الدفع NFC — بطاقة EMV Contactless كاملة عبر محرك EmvCard المُثبت.
 * المسار: PPSE ← SELECT AID (mada/Visa/MC) ← GPO ← READ RECORD ← GENERATE AC (ARQC) ← EXTERNAL AUTH.
 *
 * حد الصدق المعروف من المعمل: ARQC بمفتاح مشتق معملي وبلا BIN مرخّص —
 * أي POS حقيقي سيقرأ البطاقة كاملة ثم يأتي الرفض من الشبكة الحقيقية.
 */
public class HceService extends HostApduService {

    private EmvCard emv;
    private String emvPan;

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        WalletStore store = new WalletStore(this);
        List<Card> cards = store.load();
        Card card = store.byPan(cards, store.getSelectedPan());
        if (card == null && !cards.isEmpty()) card = cards.get(0);
        if (card == null) return EmvCard.SW_NOT_FOUND;

        // المحرك يبقى حيًّا طوال تشغيل الخدمة — ATC يتراكم عبر الجلسات كبطاقة حقيقية
        if (emv == null || !card.pan.equals(emvPan)) {
            emv = new EmvCard(card.pan, card.expiry, card.holder);
            emvPan = card.pan;
        }
        return emv.process(apdu);
    }

    @Override
    public void onDeactivated(int reason) {
        if (emv != null) emv.onDeactivated();
    }
}
