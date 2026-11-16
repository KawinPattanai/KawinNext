ซิงก์ upstream (morphe-patches 2.3.0-dev.7, extension library 2.2.0, patcher 2.4.0) พร้อมชุดแก้การ spoof สตรีมให้วิดีโอเล่นได้จริง และแพตช์ใหม่ของ YouTube / YouTube Music — versionCode 20209

**YouTube**

แก้ไข
- Spoof video streams: request ที่ spoof ไว้แต่เล่นไม่ได้ถูกทิ้ง แล้วใช้ request ของแอปแทน — url ว่าง, ไม่มี scheme, ไม่มี header หรือเป็น googlevideo url ที่ไม่มี `fvip`/`mn` (พารามิเตอร์ที่ตัวเล่นอ่านทุกครั้งและโยน null pointer เมื่อขาด) ทดสอบบน 21.45.82: null pointer 0, failed load 0, invalid url 0
- Spoof video streams (ฝั่งชุดแพตช์): ข้ามไคลเอนต์ที่ format url หรือ server streaming url ไม่มี `fvip`/`mn`, กันเคสหน้าเว็บ Mobile Web โหลดไม่ขึ้นและใช้ PoToken ตัว placeholder ของเว็บแทน PoToken ของวิดีโอ
- SABR: hook อ่าน flag ที่ตามการเขียน streaming data และปิด SABR เฉพาะเมื่อสตรีมของไคลเอนต์ถูกเขียนลง player response จริง — วิดีโอที่แตะจากหน้าแรกเริ่มเล่นใน 1.3 วินาที (เดิม failed load 11 ครั้งใน 30 วินาที)
- อ่านสตรีมของไคลเอนต์ด้วย `parseFrom(defaultInstance, bytes)` ตามที่ runtime ของแอปใช้ (เดิมสตรีมถูกดึงมาแล้วทิ้ง เพราะ player response ไม่มี parser/builder แบบ static)
- Mobile Web: เริ่มเล่นเร็วขึ้น (เลิกเสีย ~6 วินาทีต่อวิดีโอ), ใช้แรมไม่หมดตอนเล่นคุณภาพสูง, ไม่ติดสถานะ "เล่นไม่ได้" จนต้องรีสตาร์ตแอป, ไม่ error ตอนกลับมาจาก Shorts, `Allow AV1` รองรับไคลเอนต์ VR และ visionOS
- Return YouTube Dislike: ปุ่ม like/dislike มีไอคอน thumbs อีกครั้ง — ไอคอนที่ server ไม่ส่งมาถูกเติมจากไอคอนของแอปด้วย element hook และปุ่ม segmented ไม่เหลือช่องว่างเมื่อดึงยอด dislike จากเว็บไม่ได้
- Enhanced Ambient mode: เพิ่มตัวปรับความแรงของแสง (light strength), ตัวปรับความ smoot และ blur, แสงแสดงหลังแถวของคอมเมนต์และแผงอื่น (ไม่ใช่แค่หลังหน้า), "Keep text readable" นับคอลัมน์ที่สองบนแท็บเล็ต (cover จาก 0 เป็น ~0.5), หน้าแสงสว่างไม่กลายเป็นมืดชั่วขณะตอนเปิดวิดีโอ และข้อความอ่านง่ายไม่มาช้าเมื่องอกจาก fullscreen/miniplayer
- Restore old video action bar: รับเลย์เอาต์ของ action bar ตามที่ผู้ใช้เลือก แทนการยึดเลย์เอาต์เดียว
- อื่น ๆ จาก upstream: speed menu ของ Shorts หลังไลฟ์แชต, Explore/Premium ใน More tab, ป้ายวิดีโอของ A/B test ใหม่ ("Auto-dubbed", "Hyped"), ระยะห่างของ feed card, ตำแหน่ง Watch later ในฟีด, ชื่อวิดีโอระหว่างโหลด, drag handle ของ playlist เมื่อภาษาไม่ใช่อังกฤษ, ปุ่ม Add to queue บน thumbnail, คำบรรยายที่ YouTube มาร์กเป็น forced, รูปประกอบของโพสต์ที่ดาวน์โหลด, เสียงพื้นหลังหลังมีตัวเล่นอื่นหยุดเล่น, DeArrow (ไอคอน, การส่งและโหวต), หน้าตั้งค่าที่มีรายการใหม่แล้วเปิดไม่ขึ้น

เพิ่ม
- Downloads: สวิตช์ `Show download in video menus` (`morphe_feed_download_flyout_button`) เติมรายการดาวน์โหลดให้เมนูของวิดีโอที่แอปไม่มีรายการให้ เช่นวิดีโอที่เฉพาะสมาชิก Premium ดาวน์โหลดได้
- Downloads: คุณภาพ 1440p และ 2160p (4K) ในแผงเลือกคุณภาพ — **ยังไม่ทำงานในโหมดโมดูล** ป้ายชื่อคุณภาพทั้งสองเป็นสตริง `addResources` ที่ patcher เขียนลงรีซอร์สของแอป ซึ่งโมดูลเพิ่มรีซอร์สให้แอปอื่นไม่ได้ ในโค้ดโมดูลเตรียม hook ไว้แล้ว (`FormatTypeLabelFingerprint`, `OfflineSettingsConstructorFingerprint`, `DownloadQualityPickerCommandFingerprint`) แต่จะเห็นคุณภาพจริงก็ต่อเมื่อ **แพตช์ APK ของ YouTube ด้วย patcher 2.4.0** — ดูหมายเหตุ

**YouTube Music**

เพิ่ม
- Pause when muted: หยุดเพลงเมื่อเอาต์พุตเสียงถูกปิด (เป็น hook บน media session ที่แอปอัปเดต)
- Media notification controls: ซ่อน seekbar, ซ่อนปุ่มก่อนหน้า/ถัดไป และเปลี่ยนปุ่ม shuffle เป็นปุ่ม loop ใน notification

แก้ไข
- Glass navigation bar: ปุ่ม (label/icon) ของแถบไม่หายอีกเมื่อปิด glass — เดิมโมดูลสั่งให้ glass ซ่อน label/icon ของทุกแท็บที่แอปสร้าง แม้สวิตช์จะปิดอยู่ และปุ่ม Play ของ Liked music อยู่ในตำแหน่งที่พ้นแถบกระจก
- รองรับ YouTube Music 9.46.51 (experimental) และแก้ Jam queue sharing ที่ทำให้แอป crash บน 9.46
- Play albums songs: ไม่สนใจค่า "Don't play music videos" ระหว่างเล่นอัลบั้ม

**ทั้งโมดูล**

- ซิงก์ upstream dev: `morphe-patches` 52 คอมมิต (50 ไม่นับ merge) ตั้งแต่ 9953985 ถึง c6d29c30c, `morphe-patches-library` 7 คอมมิต (6d3a500 ถึง f481a81)
- ชุดแพตช์ 2.3.0-dev.7, extension library 2.2.0, patcher 2.4.0 (ARSCLib) — ตัวหลังสุดอยู่ใน `morphe-patches-library/gradle/libs.versions.toml`
- รวมของฝั่งโมดูล 15 คอมมิต: spoof/SABR, thumbs ของ RYD, ambient (strength/smoothing/blur/แถวแผง), music bar ตอนปิด glass, downloads และการอ่าน id ของ resource ใน fingerprint

**หมายเหตุ**

- versionName `2.2.8`, versionCode `20209` (ต่อจาก 20208 ของ 2.2.7_5 — ตรวจใน APK ที่ build แล้ว: `versionCode='20209' versionName='2.2.8'`)
- 1440p/2160p ใช้ได้เฉพาะเมื่อแพตช์ APK YouTube ด้วย patcher (Morphe) เพราะต้องมี addResources ของ patcher; โมดูลอย่างเดียวไม่ทำให้คุณภาพสองตัวนี้ปรากฏ
- branch `experiment/download-picker-1440p` เก็บไว้เป็นบันทึก ไม่ได้ merge เข้ารุ่นนี้
- FingerprintsKtTest: 178 tests, 3 failed (baseline เดิม: `misc.navigation` ×2 บน 21.44.535/21.45.82, `layout.theme` ×1 บน Music 9.45.51)
- การทดสอบบนเครื่องเป็นผลจากการทดสอบรายแพตช์ก่อนประกอบรุ่น (YouTube 21.45.82 / 21.44.535 บน MuMu); APK 2.2.8 ทั้งชุดยังไม่ได้ติดตั้งทดสอบบนเครื่อง
