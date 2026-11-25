// Bloatless: draws the Malaysian look pictures (1920 x 1080 JPEG) with CoreGraphics, no outside art.
// Run: swift tools/malaysia-pictures/draw.swift <output dir>
import AppKit
import CoreGraphics
import Foundation

let W: CGFloat = 1920, H: CGFloat = 1080
let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."

// A small seeded random, so every build draws the same pictures.
struct Rng { var s: UInt64; mutating func next() -> CGFloat { s = s &* 6364136223846793005 &+ 1442695040888963407; return CGFloat((s >> 33) % 100000) / 100000 } }

func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: a)
}

func vgrad(_ c: CGContext, _ stops: [(CGFloat, UInt32)], rect: CGRect = CGRect(x: 0, y: 0, width: W, height: H)) {
    let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: stops.map { rgb($0.1) } as CFArray, locations: stops.map { $0.0 })!
    c.saveGState(); c.clip(to: rect)
    // y grows upward in CoreGraphics: location 0 is the top of the picture.
    c.drawLinearGradient(g, start: CGPoint(x: 0, y: rect.maxY), end: CGPoint(x: 0, y: rect.minY), options: [])
    c.restoreGState()
}

func glow(_ c: CGContext, _ p: CGPoint, _ r: CGFloat, _ hex: UInt32, _ a: CGFloat) {
    let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [rgb(hex, a), rgb(hex, 0)] as CFArray, locations: [0, 1])!
    c.drawRadialGradient(g, startCenter: p, startRadius: 0, endCenter: p, endRadius: r, options: [])
}

// Darker top-left (clock) and bottom (dock), so the home screen stays readable on every picture.
func legibility(_ c: CGContext) {
    vgrad(c, [(0, 0x000000), (1, 0x000000)], rect: .zero)
    let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [rgb(0, 0), rgb(0, 0.55)] as CFArray, locations: [0, 1])!
    c.drawLinearGradient(g, start: CGPoint(x: 0, y: H * 0.45), end: CGPoint(x: 0, y: 0), options: [])
    let t = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [rgb(0, 0.45), rgb(0, 0)] as CFArray, locations: [0, 1])!
    c.drawRadialGradient(t, startCenter: CGPoint(x: 0, y: H), startRadius: 0, endCenter: CGPoint(x: 0, y: H), endRadius: 900, options: [])
}

func stars(_ c: CGContext, _ n: Int, seed: UInt64, maxY: CGFloat = H, minY: CGFloat = H * 0.35) {
    var r = Rng(s: seed)
    for _ in 0..<n {
        let p = CGPoint(x: r.next() * W, y: minY + r.next() * (maxY - minY))
        let s = 1 + r.next() * 2.4
        c.setFillColor(rgb(0xFFFFFF, 0.35 + r.next() * 0.6))
        c.fillEllipse(in: CGRect(x: p.x, y: p.y, width: s, height: s))
    }
}

func crescent(_ c: CGContext, _ p: CGPoint, _ r: CGFloat, _ hex: UInt32, bg: UInt32) {
    glow(c, p, r * 4, hex, 0.18)
    c.setFillColor(rgb(hex)); c.fillEllipse(in: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
    c.setFillColor(rgb(bg)); c.fillEllipse(in: CGRect(x: p.x - r * 0.55, y: p.y - r * 0.85, width: r * 1.85, height: r * 1.85))
}

func star(_ c: CGContext, _ p: CGPoint, _ r: CGFloat, points: Int, inner: CGFloat = 0.45) {
    let path = CGMutablePath()
    for i in 0..<(points * 2) {
        let a = CGFloat(i) * .pi / CGFloat(points) + .pi / 2
        let rr = i % 2 == 0 ? r : r * inner
        let q = CGPoint(x: p.x + cos(a) * rr, y: p.y + sin(a) * rr)
        i == 0 ? path.move(to: q) : path.addLine(to: q)
    }
    path.closeSubpath(); c.addPath(path); c.fillPath()
}

// A five-petal hibiscus (bunga raya), the national flower, as a batik motif.
func hibiscus(_ c: CGContext, _ p: CGPoint, _ r: CGFloat, petal: UInt32, line: UInt32, rot: CGFloat) {
    c.saveGState(); c.translateBy(x: p.x, y: p.y); c.rotate(by: rot)
    for i in 0..<5 {
        c.saveGState(); c.rotate(by: CGFloat(i) * 2 * .pi / 5)
        let petalPath = CGMutablePath()
        petalPath.move(to: .zero)
        petalPath.addCurve(to: CGPoint(x: 0, y: r), control1: CGPoint(x: -r * 0.75, y: r * 0.35), control2: CGPoint(x: -r * 0.55, y: r * 1.05))
        petalPath.addCurve(to: .zero, control1: CGPoint(x: r * 0.55, y: r * 1.05), control2: CGPoint(x: r * 0.75, y: r * 0.35))
        c.addPath(petalPath); c.setFillColor(rgb(petal)); c.fillPath()
        c.addPath(petalPath); c.setStrokeColor(rgb(line)); c.setLineWidth(r * 0.05); c.strokePath()
        c.move(to: .zero); c.addLine(to: CGPoint(x: 0, y: r * 0.7)); c.setLineWidth(r * 0.03); c.strokePath()
        c.restoreGState()
    }
    c.setFillColor(rgb(line)); c.fillEllipse(in: CGRect(x: -r * 0.12, y: -r * 0.12, width: r * 0.24, height: r * 0.24))
    c.restoreGState()
}

func leaf(_ c: CGContext, _ p: CGPoint, _ len: CGFloat, _ ang: CGFloat, _ hex: UInt32) {
    c.saveGState(); c.translateBy(x: p.x, y: p.y); c.rotate(by: ang)
    let path = CGMutablePath()
    path.move(to: .zero)
    path.addQuadCurve(to: CGPoint(x: len, y: 0), control: CGPoint(x: len * 0.5, y: len * 0.32))
    path.addQuadCurve(to: .zero, control: CGPoint(x: len * 0.5, y: -len * 0.32))
    c.addPath(path); c.setFillColor(rgb(hex)); c.fillPath()
    c.restoreGState()
}

func palm(_ c: CGContext, base: CGPoint, height: CGFloat, lean: CGFloat, color: UInt32) {
    let top = CGPoint(x: base.x + lean, y: base.y + height)
    c.setStrokeColor(rgb(color)); c.setLineWidth(height * 0.035); c.setLineCap(.round)
    c.move(to: base); c.addQuadCurve(to: top, control: CGPoint(x: base.x + lean * 0.2, y: base.y + height * 0.6)); c.strokePath()
    for i in 0..<9 {
        let a = CGFloat(i) / 8 * .pi * 1.1 - .pi * 0.05
        let len = height * (0.42 + 0.08 * sin(CGFloat(i)))
        let end = CGPoint(x: top.x + cos(a) * len, y: top.y + sin(a) * len * 0.45 - len * 0.25)
        let path = CGMutablePath(); path.move(to: top)
        path.addQuadCurve(to: end, control: CGPoint(x: top.x + cos(a) * len * 0.5, y: top.y + len * 0.22))
        c.addPath(path); c.setLineWidth(height * 0.028); c.strokePath()
    }
}

func save(_ name: String, _ draw: (CGContext) -> Void) {
    let cs = CGColorSpaceCreateDeviceRGB()
    let c = CGContext(data: nil, width: Int(W), height: Int(H), bitsPerComponent: 8, bytesPerRow: 0, space: cs,
                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    c.setShouldAntialias(true); c.interpolationQuality = .high
    draw(c)
    legibility(c)
    let rep = NSBitmapImageRep(cgImage: c.makeImage()!)
    // The app keeps each picture under 280 KB (ThemeWallpaperTest allows 300 KB): busy patterns get a lower quality.
    var q: CGFloat = 0.78
    var data = rep.representation(using: .jpeg, properties: [.compressionFactor: q])!
    while data.count > 280_000 && q > 0.3 { q -= 0.06; data = rep.representation(using: .jpeg, properties: [.compressionFactor: q])! }
    try! data.write(to: URL(fileURLWithPath: "\(out)/\(name).jpg"))
    print(name, data.count)
}

// Batik: hibiscus and leaves in a scattered repeat, wax-line gold on deep indigo.
save("batik") { c in
    vgrad(c, [(0, 0x1B1046), (1, 0x0A0820)])
    var r = Rng(s: 7)
    for row in 0..<6 { for col in 0..<9 {
        let p = CGPoint(x: CGFloat(col) * 240 + (row % 2 == 0 ? 0 : 120) + r.next() * 40, y: CGFloat(row) * 210 + 60 + r.next() * 30)
        for k in 0..<3 { leaf(c, CGPoint(x: p.x, y: p.y), 120, CGFloat(k) * 2.1 + r.next(), 0x2E5A4A) }
        hibiscus(c, p, 70 + r.next() * 20, petal: row % 2 == 0 ? 0x8C2A4A : 0x6B2E7A, line: 0xE8B860, rot: r.next() * 6)
    } }
}

// Songket: gold pucuk rebung (bamboo shoot) triangles and diamonds woven on deep red.
save("songket") { c in
    vgrad(c, [(0, 0x5A0E18), (1, 0x2A050B)])
    c.setStrokeColor(rgb(0xD9A441, 0.85)); c.setLineWidth(3)
    let s: CGFloat = 90
    for row in stride(from: CGFloat(-1), through: H / s + 1, by: 1) { for col in stride(from: CGFloat(-1), through: W / s + 1, by: 1) {
        let cx = col * s + (Int(row) % 2 == 0 ? 0 : s / 2), cy = row * s
        let d = CGMutablePath(); d.move(to: CGPoint(x: cx, y: cy - s * 0.4)); d.addLine(to: CGPoint(x: cx + s * 0.4, y: cy))
        d.addLine(to: CGPoint(x: cx, y: cy + s * 0.4)); d.addLine(to: CGPoint(x: cx - s * 0.4, y: cy)); d.closeSubpath()
        c.addPath(d); c.strokePath()
        c.setFillColor(rgb(0xD9A441, 0.55)); c.fillEllipse(in: CGRect(x: cx - 5, y: cy - 5, width: 10, height: 10))
    } }
    for band in [CGFloat(180), H - 260] {
        c.setFillColor(rgb(0x3A0710)); c.fill(CGRect(x: 0, y: band, width: W, height: 120))
        c.setFillColor(rgb(0xE0B050))
        for x in stride(from: CGFloat(0), to: W, by: 60) {
            let t = CGMutablePath(); t.move(to: CGPoint(x: x, y: band + 10)); t.addLine(to: CGPoint(x: x + 30, y: band + 110)); t.addLine(to: CGPoint(x: x + 60, y: band + 10)); t.closeSubpath()
            c.addPath(t); c.fillPath()
        }
    }
}

// Kampung: a house on stilts, coconut palms and the river at dusk.
save("kampung") { c in
    vgrad(c, [(0, 0x2B1B4A), (0.45, 0xC4553A), (0.62, 0xF2A65A), (0.64, 0x2A1C2E), (1, 0x120C18)])
    glow(c, CGPoint(x: 1350, y: 430), 380, 0xFFD27A, 0.55)
    c.setFillColor(rgb(0xFFE3A0)); c.fillEllipse(in: CGRect(x: 1290, y: 370, width: 120, height: 120))
    let ink: UInt32 = 0x1A1020
    c.setFillColor(rgb(ink)); c.fill(CGRect(x: 0, y: 0, width: W, height: 395))
    // the river's light
    for i in 0..<14 { c.setFillColor(rgb(0xF2A65A, 0.25)); c.fill(CGRect(x: 1230 + CGFloat(i % 3) * 30, y: 380 - CGFloat(i) * 22, width: 220 - CGFloat(i) * 12, height: 4)) }
    // rumah panggung: stilts, body, a steep roof
    c.setFillColor(rgb(ink))
    for x in [CGFloat(520), 600, 680, 760] { c.fill(CGRect(x: x, y: 380, width: 14, height: 90)) }
    c.fill(CGRect(x: 500, y: 470, width: 300, height: 150))
    let roof = CGMutablePath(); roof.move(to: CGPoint(x: 460, y: 615)); roof.addLine(to: CGPoint(x: 650, y: 760)); roof.addLine(to: CGPoint(x: 840, y: 615)); roof.closeSubpath()
    c.addPath(roof); c.fillPath()
    c.setFillColor(rgb(0xF2A65A, 0.8)); c.fill(CGRect(x: 560, y: 520, width: 40, height: 50)); c.fill(CGRect(x: 700, y: 520, width: 40, height: 50))
    palm(c, base: CGPoint(x: 300, y: 395), height: 520, lean: 60, color: ink)
    palm(c, base: CGPoint(x: 960, y: 395), height: 420, lean: -50, color: ink)
    palm(c, base: CGPoint(x: 1700, y: 395), height: 560, lean: -80, color: ink)
}

// Masjid at night: domes and minarets under a crescent and stars.
save("masjid") { c in
    let sky: UInt32 = 0x0B1634
    vgrad(c, [(0, 0x050A1C), (0.7, sky), (1, 0x18244A)])
    stars(c, 260, seed: 3)
    crescent(c, CGPoint(x: 1500, y: 860), 70, 0xF6D57A, bg: 0x06102A)
    let ink: UInt32 = 0x040814
    c.setFillColor(rgb(ink)); c.fill(CGRect(x: 0, y: 0, width: W, height: 300))
    c.fill(CGRect(x: 700, y: 300, width: 520, height: 160))
    c.fillEllipse(in: CGRect(x: 820, y: 360, width: 280, height: 280))
    c.fillEllipse(in: CGRect(x: 690, y: 380, width: 150, height: 150)); c.fillEllipse(in: CGRect(x: 1080, y: 380, width: 150, height: 150))
    for x in [CGFloat(600), 1300] {
        c.fill(CGRect(x: x, y: 300, width: 40, height: 380))
        let tip = CGMutablePath(); tip.move(to: CGPoint(x: x - 8, y: 680)); tip.addLine(to: CGPoint(x: x + 20, y: 760)); tip.addLine(to: CGPoint(x: x + 48, y: 680)); tip.closeSubpath()
        c.addPath(tip); c.fillPath()
    }
    c.setFillColor(rgb(0xF6D57A)); crescent(c, CGPoint(x: 960, y: 670), 14, 0xF6D57A, bg: ink)
    for x in stride(from: CGFloat(730), to: 1200, by: 70) { c.setFillColor(rgb(0xF6C35A, 0.75)); c.fill(CGRect(x: x, y: 330, width: 22, height: 50)) }
}

// Pantai Timur: sunrise over the South China Sea and a fishing boat.
save("pantai") { c in
    vgrad(c, [(0, 0x1E2C5A), (0.4, 0xE07A5F), (0.55, 0xF6C177), (0.56, 0x2C5B78), (1, 0x0B1D2E)])
    glow(c, CGPoint(x: 960, y: 475), 520, 0xFFD08A, 0.6)
    c.setFillColor(rgb(0xFFE7B0)); c.fillEllipse(in: CGRect(x: 880, y: 400, width: 160, height: 160))
    c.setFillColor(rgb(0x2C5B78)); c.fill(CGRect(x: 0, y: 0, width: W, height: 475))
    var r = Rng(s: 11)
    for i in 0..<40 { c.setFillColor(rgb(0xFFD08A, 0.18 + r.next() * 0.2)); let y = 470 - CGFloat(i) * 11; let w = 260 - CGFloat(i) * 5; c.fill(CGRect(x: 960 - w / 2 + (r.next() - 0.5) * 40, y: y, width: max(w, 20), height: 3)) }
    // perahu: hull and a curled prow
    let ink: UInt32 = 0x0E1420
    let hull = CGMutablePath(); hull.move(to: CGPoint(x: 1300, y: 330)); hull.addLine(to: CGPoint(x: 1620, y: 330))
    hull.addQuadCurve(to: CGPoint(x: 1680, y: 400), control: CGPoint(x: 1670, y: 340)); hull.addLine(to: CGPoint(x: 1260, y: 380)); hull.closeSubpath()
    c.addPath(hull); c.setFillColor(rgb(ink)); c.fillPath()
    c.fill(CGRect(x: 1440, y: 380, width: 8, height: 200))
    let sail = CGMutablePath(); sail.move(to: CGPoint(x: 1448, y: 570)); sail.addLine(to: CGPoint(x: 1590, y: 400)); sail.addLine(to: CGPoint(x: 1448, y: 400)); sail.closeSubpath()
    c.addPath(sail); c.fillPath()
}

// Tea hills: Cameron Highlands' rows of green under morning mist.
save("tanah-tinggi") { c in
    vgrad(c, [(0, 0xBFD8D2), (0.5, 0xE6EFE4), (1, 0x86A88F)])
    let layers: [(CGFloat, UInt32)] = [(640, 0x7FA88A), (520, 0x5C8F6A), (380, 0x3F7350), (220, 0x2A5A3A), (80, 0x1C4128)]
    for (i, l) in layers.enumerated() {
        let p = CGMutablePath(); p.move(to: CGPoint(x: 0, y: 0)); p.addLine(to: CGPoint(x: 0, y: l.0))
        for x in stride(from: CGFloat(0), through: W, by: 40) { p.addLine(to: CGPoint(x: x, y: l.0 + 60 * sin(x / 260 + CGFloat(i) * 1.7) + 30 * sin(x / 90 + CGFloat(i)))) }
        p.addLine(to: CGPoint(x: W, y: 0)); p.closeSubpath()
        c.addPath(p); c.setFillColor(rgb(l.1)); c.fillPath()
        c.setStrokeColor(rgb(0xFFFFFF, 0.08)); c.setLineWidth(3)
        for k in 1..<5 { c.move(to: CGPoint(x: 0, y: l.0 - CGFloat(k) * 26)); for x in stride(from: CGFloat(0), through: W, by: 40) { c.addLine(to: CGPoint(x: x, y: l.0 - CGFloat(k) * 26 + 60 * sin(x / 260 + CGFloat(i) * 1.7))) }; c.strokePath() }
        glow(c, CGPoint(x: W * 0.3 + CGFloat(i) * 260, y: l.0 + 20), 500, 0xFFFFFF, 0.12)
    }
}

// Raya: ketupat woven in green and gold, and pelita lights.
save("raya") { c in
    vgrad(c, [(0, 0x0F3A22), (1, 0x06180E)])
    var r = Rng(s: 21)
    for i in 0..<9 {
        let p = CGPoint(x: 180 + CGFloat(i) * 200 + r.next() * 40, y: 620 + r.next() * 300)
        let s: CGFloat = 70 + r.next() * 30
        c.setStrokeColor(rgb(0xD8B14A, 0.9)); c.setLineWidth(2); c.move(to: CGPoint(x: p.x, y: H)); c.addLine(to: CGPoint(x: p.x, y: p.y + s)); c.strokePath()
        c.saveGState(); c.translateBy(x: p.x, y: p.y); c.rotate(by: .pi / 4)
        c.setFillColor(rgb(0x7DB24A)); c.fill(CGRect(x: -s * 0.7, y: -s * 0.7, width: s * 1.4, height: s * 1.4))
        c.setStrokeColor(rgb(0xE8D27A)); c.setLineWidth(3)
        for k in stride(from: -s * 0.7, through: s * 0.7, by: s * 0.35) { c.move(to: CGPoint(x: k, y: -s * 0.7)); c.addLine(to: CGPoint(x: k, y: s * 0.7)); c.move(to: CGPoint(x: -s * 0.7, y: k)); c.addLine(to: CGPoint(x: s * 0.7, y: k)) }
        c.strokePath(); c.restoreGState()
    }
    for x in stride(from: CGFloat(60), to: W, by: 110) { glow(c, CGPoint(x: x, y: 230), 70, 0xFFB648, 0.55); c.setFillColor(rgb(0xFFD38A)); c.fillEllipse(in: CGRect(x: x - 9, y: 221, width: 18, height: 18)) }
}

// Ramadan: lanterns and a crescent over deep teal.
save("ramadan") { c in
    vgrad(c, [(0, 0x04161C), (1, 0x0B3A40)])
    stars(c, 160, seed: 5, minY: H * 0.5)
    crescent(c, CGPoint(x: 1560, y: 820), 90, 0xF2D27A, bg: 0x072227)
    for (i, x) in [CGFloat(420), 620, 820].enumerated() {
        let y: CGFloat = 640 - CGFloat(i % 2) * 90
        c.setStrokeColor(rgb(0xC9A24A)); c.setLineWidth(2); c.move(to: CGPoint(x: x, y: H)); c.addLine(to: CGPoint(x: x, y: y + 120)); c.strokePath()
        glow(c, CGPoint(x: x, y: y + 50), 180, 0xFFB648, 0.45)
        let body = CGMutablePath(); body.move(to: CGPoint(x: x - 40, y: y)); body.addLine(to: CGPoint(x: x + 40, y: y)); body.addLine(to: CGPoint(x: x + 55, y: y + 60))
        body.addLine(to: CGPoint(x: x + 40, y: y + 110)); body.addLine(to: CGPoint(x: x - 40, y: y + 110)); body.addLine(to: CGPoint(x: x - 55, y: y + 60)); body.closeSubpath()
        c.addPath(body); c.setFillColor(rgb(0xF6C35A, 0.85)); c.fillPath()
        c.addPath(body); c.setStrokeColor(rgb(0x8A5A1A)); c.setLineWidth(3); c.strokePath()
        let cap = CGMutablePath(); cap.move(to: CGPoint(x: x - 40, y: y + 110)); cap.addLine(to: CGPoint(x: x, y: y + 150)); cap.addLine(to: CGPoint(x: x + 40, y: y + 110)); cap.closeSubpath()
        c.addPath(cap); c.setFillColor(rgb(0xC9A24A)); c.fillPath()
    }
}

// Merdeka: red and white stripes waving, with a navy field, a crescent and a fourteen-point star.
save("merdeka") { c in
    vgrad(c, [(0, 0x0A0F24), (1, 0x05070F)])
    let top: CGFloat = 900, stripe: CGFloat = 40
    for i in 0..<14 {
        let p = CGMutablePath()
        let y0 = top - CGFloat(i) * stripe
        p.move(to: CGPoint(x: 0, y: y0))
        for x in stride(from: CGFloat(0), through: W, by: 30) { p.addLine(to: CGPoint(x: x, y: y0 + 40 * sin(x / 320))) }
        for x in stride(from: W, through: 0, by: -30) { p.addLine(to: CGPoint(x: x, y: y0 - stripe + 40 * sin(x / 320))) }
        p.closeSubpath(); c.addPath(p); c.setFillColor(rgb(i % 2 == 0 ? 0xC8102E : 0xF2F2F2, 0.9)); c.fillPath()
    }
    let canton = CGMutablePath(); canton.move(to: CGPoint(x: 0, y: top))
    for x in stride(from: CGFloat(0), through: 900, by: 30) { canton.addLine(to: CGPoint(x: x, y: top + 40 * sin(x / 320))) }
    for x in stride(from: CGFloat(900), through: 0, by: -30) { canton.addLine(to: CGPoint(x: x, y: top - stripe * 8 + 40 * sin(x / 320))) }
    canton.closeSubpath(); c.addPath(canton); c.setFillColor(rgb(0x010066)); c.fillPath()
    crescent(c, CGPoint(x: 330, y: 760), 120, 0xFFCC00, bg: 0x010066)
    c.setFillColor(rgb(0xFFCC00)); star(c, CGPoint(x: 560, y: 760), 95, points: 14, inner: 0.5)
}
