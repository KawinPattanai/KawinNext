แถบนำทางแบบกระจก (glass navigation bar) ของ YouTube บนพื้นฐาน 2.2.7 — ไม่มีแพตช์ใหม่ทั้งชุด ชุดแพตช์และไลบรารีเวอร์ชันเดิม

**YouTube**

เพิ่ม
- Navigation bar: แถบนำทางล่างของ YouTube วาดเป็นแคปซูลโปร่งแสงลอยเหนือเนื้อหา เปิดด้วยสวิตช์ `morphe_navigation_bar_glass` ในหน้าตั้งค่า พร้อมตัวปรับความทึบ `morphe_navigation_bar_glass_opacity` และสวิตช์ปลายมน `morphe_navigation_bar_glass_round`
- จุดยึดของแถบ: `InitializeBottomBarContainerFingerprint` (resource `bottom_bar_container` + `Landroid/view/View;->addOnLayoutChangeListener`) คัดลอกมาจาก upstream ตรงตัว แล้วเรียก `NavigationBarPatch.setNavigationBarGlass(View)` ของ extension หลังแอปสร้าง container ของแถบ; extension ปล่อยแถบไว้เหมือนเดิมเมื่อสวิตช์ปิด

**หมายเหตุ**
- versionCode 20208 (ต่อจาก 20207 ของ 2.2.7), patch set 2.3.0-dev.4, extension library 2.2.0-dev.6
- ตัวปรับขนาดแคปซูลของ upstream (`morphe_navigation_bar_glass_height` + `YouTubeGlassNavigationBarPreference`) ยังไม่ถูกต่อในโมดูล รอบนี้จึงใช้ขนาดตั้งต้นของ extension (สูง 100%, กว้าง 100%, ระยะปุ่ม 48dp)
- รอบนี้ยังไม่ได้ทดสอบแถบกระจกบนเครื่อง (ตรวจ build และ fingerprint test เท่านั้น)
- FingerprintsKtTest: 177 tests, 3 failed (baseline เดิม: misc.navigation ×2, layout.theme ×1)
