# محفظة nexo-tms (NexoWallet)

تطبيق المحفظة لمشروع الإصدار nexo-tms — عرض بطاقات المُصدر + دفع NFC (HCE).

## الميزات
- **بطاقات المُصدر**: جلب حي من `GET /issuer/cards` برأس `X-Issuer-Token` (العقد الموثق من issuer_core.py) — آخر 4، الحالة، الانتهاء، الحساب. PAN/CVV مكتومان عند المُصدر (وعي PCI).
- **بطاقاتي للدفع NFC**: بطاقات محلية ببيانات كاملة، تُقدَّم لأي قارئ EMV Contactless (PPSE → SELECT AID → GPO → READ RECORD → GENERATE AC → EXTERNAL AUTH) عبر محرك EmvCard المُثبت.
- التوكن يُعرض ويُخزَّن نصًّا صريحًا (سياق معملي بطلب المالك).

## حد الصدق
البطاقة بلا BIN مرخّص وARQC بمفتاح مشتق معملي — أي POS حقيقي يقرأها EMV كاملة ثم يأتي الرفض من الشبكة الحقيقية. القيمة: اختبار كامل لمسار القارئ وتشخيص أي طبقة تتعطل.

## البناء
GitHub Actions يبني `NexoWallet.apk` عند كل دفعة (بدون Gradle: aapt2 + javac + d8 + apksigner).
