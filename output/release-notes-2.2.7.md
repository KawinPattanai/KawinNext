แพตช์ใหม่ของ YouTube และ YouTube Music พร้อมการแก้ให้ชุดแพตช์ติดตั้งได้จริงบน YouTube 21.44–21.45 (ทดสอบบน MuMu)

**YouTube**

เพิ่ม
- Autoplay control: สวิตช์เปิด/ปิดการเล่นอัตโนมัติของตัวเล่น
- Playlist button: ปุ่มเปิด playlist panel จากตัวเล่น พร้อม router ของ panel ที่ต่อผ่าน proxy เพราะโมดูลเพิ่มเมธอดให้คลาสของแอปไม่ได้
- Fullscreen playlist panel: แสดง playlist panel ทับจอเต็มบนแท็บเล็ต แทนการออกจาก fullscreen
- Fix media session stop: คง notification และเสียงไว้เมื่อปิด panel ของ comment replies
- Miniplayer พร้อม glass: เลย์เอาต์ 18 fingerprint และหน้าตั้งค่าของตัวเอง โดย glass วาดผ่าน hook บน draw ของ seekbar แทน filter ของ upstream ที่ไม่ตอบบนทั้งสองบิลด์
- Spoof video streams พร้อมไคลเอนต์ Mobile Web: พอร์ต request builder, transport และ player configuration มาพร้อม fingerprint และใช้ proxy แทน smali ที่ upstream ฉีดเข้าแอป; header ของ media request อ่านจาก field จริงของคลาส (ทดสอบบน 21.45.82: วิดีโอเล่นได้ ไม่มี fatal)
- Feed thumbnail like position: เลือกตำแหน่งของตัวเลข like ในฟีด ผ่านหน้าจอ settings ของโมดูลเอง

แก้ไข
- Native playlist queue และ Add to queue ติดตั้งได้บน 21.44.535: อ่าน constructor ให้เป็น constructor, อ่านชนิดของ field จาก signature ของมันเอง, เดิน interface ของคลาสด้วย, ข้าม hook ของ abstract method แทนที่จะทำให้แพตช์ล้ม, หา wrapper ของคิวผ่าน navigator interface, หา sequencer และ navigator ของ playlist header, และอ่าน press callback ของ player controls ตามรูปแบบที่ 21.44 ใช้
- แอปเริ่มทำงานได้ระหว่างติดแพตช์: resolve fingerprint บน thread ของตัวเอง และเขียน cache หลังทุกแพตช์แทนที่จะเขียนครั้งเดียวตอนจบ (แก้ปัญหาแอปค้างที่ splash แล้วถูกฆ่า)
- Contextual menu ของ 21.45: อ่านจากสีที่ resolve แทนความกว้างที่ชื่อไว้ และอ่าน field ของ click handler โดยไม่พึ่งชนิดที่มันไม่มีแล้ว
- Ambient mode และ battery saver bypass: ไม่ล้มทั้งแพตช์เมื่อ sink เป็น abstract, เดิน interface ของคลาส, และอ่าน constructor ผ่าน accessor ที่ถูกต้อง
- Swipe controls: host ที่ activity เก็บไว้อ่านเป็น nullable และ hook navigator check ในจุดที่ player เขียน แทนเมธอด abstract บน interface
- On-the-go: อ่านสถานะจาก state object ที่แอปถืออยู่ แทนการฉีดเมธอดแบบ upstream

**YouTube Music**

เพิ่ม
- Disable auto captions
- Remember video quality: จำคุณภาพที่เลือกจากเมนู โดยห่อ menu object ของแอปด้วย adapter
- SponsorBlock: time bar, endpoints และ preferences พร้อมการเก็บ segment ไว้ดูตอน offline

แก้ไข
- ชื่อหน้าตั้งค่าตรงกับ string จริง (about, ads, feed, general, misc, player) — เดิมไม่มีหัวข้อหรือใช้เลขหลักเดียว

**ทั้งโมดูล**

- สวิตช์ในหน้าตั้งค่าเขียนลง Setting ของ extension จริง ไม่ใช่แค่ preference store (ค่ามีผลทันทีและมี prompt ให้รีสตาร์ต)
- ซิงก์ upstream dev: morphe-patches 60 commits และ morphe-patches-library 3 commits
- อัปเดต patcher เป็น 2.4.0-dev.4 (ARSCLib)

**หมายเหตุ**
- Patch set 2.3.0-dev.4, extension library 2.2.0-dev.6, patcher 2.4.0-dev.4, versionCode 20207
- 2.2.6 ถูก bump ไว้ที่ commit 6d92cf6 แต่ไม่เคยมี tag หรือ release — 2.2.7 จึงรวมทุกอย่างตั้งแต่ 2.2.5 (37 commits)
- FingerprintsKtTest: 177 tests, 3 failed (baseline เดิม: misc.navigation ×2, layout.theme ×1)
