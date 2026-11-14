package com.nexotms.wallet;

import android.nfc.tech.IsoDep;

import java.util.ArrayList;

/**
 * قارئ EMV Contactless — الوجه القارئ من محرك معملنا:
 * SELECT PPSE → استخراج AID → SELECT AID → GPO (PDOL معدومات صفرية)
 * → READ RECORD عبر AFL → استخراج PAN/الصلاحية/track2/ATC.
 * كل الردود الخام تُسجَّل — الحقيقة عند النزاع.
 * القراءة محلية بحتة: لا يُرسل شيء لأي شبكة ولا سيرفر.
 */
public class EmvReader {

    public static class ReadResult {
        public boolean ok = false;
        public String error = "";
        public String pan = "";
        public String expiry = "";   // YYMM كما في 5F24
        public String track2 = "";
        public String aid = "";
        public String aidName = "";
        public String atc = "";
        public final StringBuilder log = new StringBuilder();
    }

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private static String aidName(String aidHex) {
        if (aidHex.startsWith("A000000228")) return "mada";
        if (aidHex.startsWith("A000000003")) return "Visa";
        if (aidHex.startsWith("A000000004")) return "Mastercard";
        if (aidHex.startsWith("A000000025")) return "American Express";
        return "غير معروف";
    }

    /** APDU تنفيذية مع تسجيل الرد الخام */
    private static byte[] x(IsoDep iso, ReadResult r, byte[] cmd, String label)
            throws Exception {
        byte[] resp = iso.transceive(cmd);
        StringBuilder sb = new StringBuilder();
        sb.append("→ ").append(label).append(": ").append(hex(cmd)).append("\n");
        sb.append("← ").append(hex(resp)).append("\n\n");
        r.log.append(sb);
        return resp;
    }

    private static byte[] select(byte[] data) {
        byte[] cmd = new byte[5 + data.length + 1];
        cmd[0] = 0x00; cmd[1] = (byte) 0xA4; cmd[2] = 0x04; cmd[3] = 0x00;
        cmd[4] = (byte) data.length;
        System.arraycopy(data, 0, cmd, 5, data.length);
        cmd[cmd.length - 1] = 0x00;
        return cmd;
    }

    private static byte[] hexBytes(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static boolean ok(byte[] resp) {
        return resp.length >= 2 && resp[resp.length - 2] == (byte) 0x90
                && resp[resp.length - 1] == 0x00;
    }

    // ===== محلل TLV مصغّر (وسوم 1-2 بايت وأطوال 81/82) =====
    private static class Tlv {
        int tagHi; int tagLo; byte[] val;
        Tlv(int hi, int lo, byte[] v) { tagHi = hi; tagLo = lo; val = v; }
        boolean is(int hi, int lo) { return tagHi == hi && (lo == -2 || tagLo == lo); }
    }

    private static ArrayList<Tlv> parseTlv(byte[] b) {
        ArrayList<Tlv> out = new ArrayList<Tlv>();
        int i = 0;
        while (i + 1 < b.length) {
            int t1 = b[i] & 0xFF;
            int t2 = -1;
            if ((t1 & 0x1F) == 0x1F && i + 1 < b.length) { t2 = b[i + 1] & 0xFF; i += 2; }
            else i += 1;
            if (i >= b.length) break;
            int len = b[i] & 0xFF;
            i += 1;
            if (len == 0x81) { len = b[i] & 0xFF; i += 1; }
            else if (len == 0x82) { len = ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF); i += 2; }
            if (len > b.length - i) len = b.length - i;
            byte[] v = new byte[len];
            System.arraycopy(b, i, v, 0, len);
            out.add(new Tlv(t1, t2, v));
            i += len;
        }
        return out;
    }

    private static Tlv find(ArrayList<Tlv> l, int hi, int lo) {
        for (Tlv t : l) {
            if (t.is(hi, lo)) return t;
            if (t.tagHi == 0x6F || t.tagHi == 0xA5 || t.tagHi == 0x77
                    || t.tagHi == 0x70 || t.tagHi == 0x61) {
                Tlv d = find(parseTlv(t.val), hi, lo);
                if (d != null) return d;
            }
        }
        return null;
    }

    private static Tlv find1(ArrayList<Tlv> l, int hi) {
        return find(l, hi, -2);
    }

    /** بناء بيانات GPO من PDOL (9F38) بقيم صفرية (مصفوفة جافا تبدأ أصفارًا) */
    private static byte[] gpoData(byte[] pdol) {
        int total = 0;
        int i = 0;
        while (i + 1 < pdol.length) {
            total += pdol[i + 1] & 0xFF;
            i += 2;
        }
        byte[] out = new byte[2 + total];
        out[0] = (byte) 0x83; out[1] = (byte) total;
        return out;
    }

    public static ReadResult read(IsoDep iso) {
        ReadResult r = new ReadResult();
        try {
            iso.connect();
            iso.setTimeout(15000);

            // 1) SELECT PPSE — بيئة الدفع غير التلامسية
            byte[] resp = x(iso, r,
                    select(hexBytes("325041592E5359532E4444463031")), "SELECT PPSE");
            if (!ok(resp)) {
                r.error = "رفض الشريحة SELECT PPSE (SW " + hex(new byte[]{resp[resp.length - 2],
                        resp[resp.length - 1]}) + ") — ليست بطاقة دفع تلامسية";
                return r;
            }
            Tlv aidT = find(parseTlv(resp), 0x4F, -2);
            if (aidT == null) aidT = find1(parseTlv(resp), 0x4F);
            if (aidT == null) {
                r.error = "لا AID في PPSE";
                return r;
            }
            r.aid = hex(aidT.val);
            r.aidName = aidName(r.aid);

            // 2) SELECT AID
            resp = x(iso, r, select(aidT.val), "SELECT " + r.aidName);
            if (!ok(resp)) {
                r.error = "رفض SELECT AID";
                return r;
            }
            ArrayList<Tlv> fci = parseTlv(resp);
            Tlv pdolT = find(fci, 0x9F, 0x38);

            // 3) GPO
            byte[] gpoCmd;
            {
                byte[] data = gpoData(pdolT == null ? new byte[0] : pdolT.val);
                gpoCmd = new byte[5 + data.length + 1];
                gpoCmd[0] = (byte) 0x80; gpoCmd[1] = (byte) 0xA8;
                gpoCmd[2] = 0x00; gpoCmd[3] = 0x00;
                gpoCmd[4] = (byte) data.length;
                System.arraycopy(data, 0, gpoCmd, 5, data.length);
                gpoCmd[gpoCmd.length - 1] = 0x00;
            }
            resp = x(iso, r, gpoCmd, "GPO");
            ArrayList<Tlv> gpo = ok(resp) ? parseTlv(resp) : new ArrayList<Tlv>();

            // 4) READ RECORD عبر AFL (أو SFI افتراضية عند غيابه)
            int[][] afl;
            Tlv aflT = find(gpo, 0x94, -2);
            if (aflT == null) aflT = find(fci, 0x94, -2);
            if (aflT != null && aflT.val.length >= 4) {
                int n = aflT.val.length / 4;
                afl = new int[n][3];
                for (int i = 0; i < n; i++) {
                    afl[i][0] = aflT.val[i * 4] & 0xFF;
                    afl[i][1] = aflT.val[i * 4 + 1] & 0xFF;
                    afl[i][2] = aflT.val[i * 4 + 2] & 0xFF;
                }
            } else {
                afl = new int[][]{{1, 1, 4}, {2, 1, 4}};
            }
            ArrayList<Tlv> all = new ArrayList<Tlv>(gpo);
            for (int[] e : afl) {
                for (int rec = e[1]; rec <= e[2]; rec++) {
                    byte[] cmd = new byte[5];
                    cmd[0] = 0x00; cmd[1] = (byte) 0xB2; cmd[2] = (byte) rec;
                    cmd[3] = (byte) ((e[0] << 3) | 0x04); cmd[4] = 0x00;
                    byte[] rr = x(iso, r, cmd, "READ RECORD SFI" + e[0] + " #" + rec);
                    if (ok(rr)) all.addAll(parseTlv(rr));
                }
            }

            // 5) استخراج الحقول
            Tlv panT = find(all, 0x5A, -2);
            Tlv tr2T = find(all, 0x57, -2);
            Tlv expT = find(all, 0x5F, 0x24);
            Tlv atcT = find(all, 0x9F, 0x36);
            if (panT != null) r.pan = hex(panT.val).replaceAll("F+$", "");
            if (tr2T != null) {
                String t2 = hex(tr2T.val);
                r.track2 = t2;
                if (r.pan.isEmpty()) {
                    int d = t2.indexOf('D');
                    if (d > 0) r.pan = t2.substring(0, d).replaceAll("F+$", "");
                    if (d > 0 && d + 5 <= t2.length()) r.expiry = t2.substring(d + 1, d + 5);
                }
            }
            if (expT != null && expT.val.length >= 2) {
                r.expiry = String.format("%02X%02X", expT.val[0], expT.val[1]);
            }
            if (atcT != null) r.atc = hex(atcT.val);

            if (r.pan.isEmpty()) {
                r.error = "لم يُعثر على PAN في السجلات — راجع الردود الخام";
                return r;
            }
            r.ok = true;
            return r;
        } catch (Exception e) {
            r.error = String.valueOf(e);
            return r;
        } finally {
            try { iso.close(); } catch (Exception ignored) { }
        }
    }
}
