package com.nexotms.wallet;

import android.nfc.tech.IsoDep;

import java.util.ArrayList;

/**
 * قارئ EMV Contactless — النسخة التشخيصية:
 * 1) تقرير تقني أولًا: ATS/الوسوم من الشريحة قبل أي حوار.
 * 2) PPSE ثم PSE ثم تجربة AID معروفة مباشرة (mada/Visa/MC).
 * 3) GPO بقيم صفرية — وعند فشله نكمل بالسجلات مباشرة.
 * 4) كنس احتياطي SFI 1..4 سجل 1..10 عند غياب AFL.
 * 5) كل تبادل APDU يُسجَّل خامًا — لا صمت أبدًا، الفشل يُفسَّر بالسطور.
 * القراءة محلية بحتة: لا يُرسل بايت لأي سيرفر.
 */
public class EmvReader {

    public static class ReadResult {
        public boolean ok = false;
        public String error = "";
        public String pan = "";
        public String expiry = "";
        public String track2 = "";
        public String aid = "";
        public String aidName = "";
        public String atc = "";
        public String tech = "";
        public final StringBuilder log = new StringBuilder();
    }

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private static byte[] hexBytes(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String aidName(String aidHex) {
        if (aidHex.startsWith("A000000228")) return "mada";
        if (aidHex.startsWith("A000000003")) return "Visa";
        if (aidHex.startsWith("A000000004")) return "Mastercard";
        if (aidHex.startsWith("A000000025")) return "American Express";
        if (aidHex.startsWith("A000000065")) return "JCB";
        return "غير معروف (" + aidHex + ")";
    }

    private static byte[] select(byte[] data) {
        byte[] cmd = new byte[5 + data.length + 1];
        cmd[0] = 0x00; cmd[1] = (byte) 0xA4; cmd[2] = 0x04; cmd[3] = 0x00;
        cmd[4] = (byte) data.length;
        System.arraycopy(data, 0, cmd, 5, data.length);
        cmd[cmd.length - 1] = 0x00;
        return cmd;
    }

    private static boolean ok(byte[] resp) {
        return resp != null && resp.length >= 2
                && resp[resp.length - 2] == (byte) 0x90 && resp[resp.length - 1] == 0x00;
    }

    private static String sw(byte[] resp) {
        if (resp == null || resp.length < 2) return "????";
        return String.format("%02X%02X", resp[resp.length - 2], resp[resp.length - 1]);
    }

    // ===== محلل TLV: وسوم 1-2 بايت وأطوال 81/82 =====
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
            if ((t1 & 0x1F) == 0x1F) { t2 = b[i + 1] & 0xFF; i += 2; }
            else i += 1;
            if (i >= b.length) break;
            int len = b[i] & 0xFF;
            i += 1;
            if (len == 0x81 && i < b.length) { len = b[i] & 0xFF; i += 1; }
            else if (len == 0x82 && i + 1 < b.length) {
                len = ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF); i += 2;
            }
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
                    || t.tagHi == 0x70 || t.tagHi == 0x61 || t.tagHi == 0x67) {
                Tlv d = find(parseTlv(t.val), hi, lo);
                if (d != null) return d;
            }
        }
        return null;
    }

    private static ArrayList<Tlv> findAll(ArrayList<Tlv> l, int hi, int lo,
                                           ArrayList<Tlv> acc) {
        for (Tlv t : l) {
            if (t.is(hi, lo)) acc.add(t);
            if (t.tagHi == 0x6F || t.tagHi == 0xA5 || t.tagHi == 0x77
                    || t.tagHi == 0x70 || t.tagHi == 0x61 || t.tagHi == 0x67) {
                findAll(parseTlv(t.val), hi, lo, acc);
            }
        }
        return acc;
    }

    /** بناء بيانات GPO من PDOL بقيم صفرية */
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

    private static void logX(ReadResult r, String cmdHex, byte[] resp, String label) {
        r.log.append("→ ").append(label).append(": ").append(cmdHex).append("\n");
        r.log.append("← ").append(hex(resp)).append("\n\n");
    }

    /** محاولة كاملة لتطبيق واحد: GPO ثم كنس السجلات ثم استخراج الحقول */
    private static boolean tryApp(IsoDep iso, ReadResult r, byte[] aid) throws Exception {
        String aidHex = hex(aid);
        byte[] sel = select(aid);
        byte[] resp = iso.transceive(sel);
        logX(r, hex(sel), resp, "SELECT AID " + aidHex);
        if (!ok(resp)) return false;
        r.aid = aidHex;
        r.aidName = aidName(aidHex);
        r.log.append("═ فتح التطبيق ").append(r.aidName).append(" ═\n");

        ArrayList<Tlv> fci = parseTlv(resp);
        Tlv pdolT = find(fci, 0x9F, 0x38);
        byte[] gpoCmd;
        {
            byte[] data = gpoData(pdolT == null ? new byte[0] : pdolT.val);
            gpoCmd = new byte[5 + data.length + 1];
            gpoCmd[0] = (byte) 0x80; gpoCmd[1] = (byte) 0xA8;
            gpoCmd[4] = (byte) data.length;
            System.arraycopy(data, 0, gpoCmd, 5, data.length);
        }
        resp = iso.transceive(gpoCmd);
        logX(r, hex(gpoCmd), resp, "GPO");
        ArrayList<Tlv> all = new ArrayList<Tlv>();
        if (ok(resp)) all.addAll(parseTlv(resp));

        // مسار AFL
        Tlv aflT = find(all, 0x94, -2);
        if (aflT == null) aflT = find(fci, 0x94, -2);
        ArrayList<int[]> afl = new ArrayList<int[]>();
        if (aflT != null && aflT.val.length >= 4) {
            for (int i = 0; i + 3 < aflT.val.length; i += 4) {
                afl.add(new int[]{aflT.val[i] & 0xFF, aflT.val[i + 1] & 0xFF,
                        aflT.val[i + 2] & 0xFF});
            }
        } else {
            for (int sfi = 1; sfi <= 4; sfi++) afl.add(new int[]{sfi, 1, 10});
        }
        for (int[] e : afl) {
            for (int rec = e[1]; rec <= e[2] && rec <= 10; rec++) {
                byte[] cmd = new byte[5];
                cmd[0] = 0x00; cmd[1] = (byte) 0xB2; cmd[2] = (byte) rec;
                cmd[3] = (byte) ((e[0] << 3) | 0x04); cmd[4] = 0x00;
                byte[] rr = iso.transceive(cmd);
                logX(r, hex(cmd), rr, "READ RECORD SFI" + e[0] + " #" + rec);
                if (ok(rr)) all.addAll(parseTlv(rr));
            }
        }

        // استخراج الحقول
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
                if (d > 0) {
                    r.pan = t2.substring(0, d).replaceAll("F+$", "");
                    if (d + 5 <= t2.length()) r.expiry = t2.substring(d + 1, d + 5);
                }
            }
        }
        if (expT != null && expT.val.length >= 2) {
            r.expiry = String.format("%02X%02X", expT.val[0], expT.val[1]);
        }
        if (atcT != null) r.atc = hex(atcT.val);
        return !r.pan.isEmpty();
    }

    public static ReadResult read(IsoDep iso) {
        ReadResult r = new ReadResult();
        try {
            iso.connect();
            iso.setTimeout(15000);
            byte[] ats = iso.getHistoricalBytes();
            r.tech = "ATS/السجل التاريخي: " + (ats == null ? "لا يوجد" : hex(ats));
            r.log.append(r.tech).append("\n\n");

            // 1) جمع مرشحي AID: PPSE ثم PSE
            ArrayList<byte[]> cands = new ArrayList<byte[]>();
            String[] dd = {"325041592E5359532E4444463031",
                    "315041592E5359532E4444463031"};
            for (String d : dd) {
                byte[] sel = select(hexBytes(d));
                byte[] resp = iso.transceive(sel);
                logX(r, hex(sel), resp,
                        d.startsWith("32") ? "SELECT PPSE" : "SELECT PSE");
                if (ok(resp)) {
                    ArrayList<Tlv> aids = findAll(parseTlv(resp), 0x4F, -2,
                            new ArrayList<Tlv>());
                    for (Tlv t : aids) {
                        if (t.val.length >= 5 && t.val.length <= 16
                                && hex(t.val).startsWith("A0")) cands.add(t.val);
                    }
                }
            }
            // 2) احتياط: AID معروفة مباشرة (mada ثم Visa ثم MC)
            String[] known = {"A0000002281010", "A0000000031010",
                    "A0000000041010", "A000000003101001", "A00000000430600"};
            for (String k : known) {
                boolean have = false;
                for (byte[] c : cands) if (hex(c).toUpperCase().startsWith(k)) have = true;
                if (!have) cands.add(hexBytes(k));
            }

            if (cands.isEmpty()) {
                r.error = "لا مرشحي AID — البطاقة لم تفتح PPSE ولا PSE";
                return r;
            }
            r.log.append("المرشحون: ").append(cands.size()).append("\n\n");
            for (byte[] aid : cands) {
                if (tryApp(iso, r, aid)) {
                    r.ok = true;
                    return r;
                }
            }
            r.error = "جرّب كل " + cands.size() + " مرشحًا ولم يُستخرج PAN — راجع الردود الخام أعلاه";
            return r;
        } catch (Exception e) {
            r.error = String.valueOf(e);
            return r;
        } finally {
            try { iso.close(); } catch (Exception ignored) { }
        }
    }
}
