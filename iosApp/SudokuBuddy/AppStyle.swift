import SwiftUI
import UIKit

/// Same semantic overlay colors as Android's Ui.kt. Amber is reserved for recognition doubt.
enum AppStyle {
    static func puzzleFont(size: CGFloat,weight: Font.Weight = .regular) -> Font {
        let name=weight == .bold ? "Roboto-Bold" : weight == .medium ? "Roboto-Medium" : "Roboto-Regular"
        assert(UIFont(name:name,size:size) != nil,"Missing bundled puzzle font")
        return .custom(name,fixedSize:size)
    }
    static let background = Color(red: 20/255, green: 18/255, blue: 24/255)
    static let drawer = Color(red: 30/255, green: 28/255, blue: 34/255)
    static let surface = Color(red: 43/255, green: 41/255, blue: 48/255)
    static let onSurface = Color(red: 230/255, green: 225/255, blue: 233/255)
    static let secondary = Color(red: 202/255, green: 196/255, blue: 208/255)
    static let primary = Color(red: 208/255, green: 188/255, blue: 255/255)
    static let correct = Color(red: 102/255, green: 187/255, blue: 106/255)
    static let accent = Color(red: 77/255, green: 208/255, blue: 225/255)
    static let written = Color(red: 186/255, green: 104/255, blue: 200/255)
    static let solution = Color(red: 79/255, green: 195/255, blue: 247/255)
    static let incorrect = Color(red: 239/255, green: 83/255, blue: 80/255)
    static let uncertain = Color(red: 1, green: 179/255, blue: 0)
}

/// Android's selected filled pill and unselected outline, with a 48pt tap target.
struct PuzzleActionStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    var active = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(AndroidText.labelLarge).lineLimit(1)
            .padding(.horizontal, 12).frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(active ? AppStyle.background : AppStyle.secondary)
            .background(active ? AppStyle.primary : .clear, in: Capsule())
            .overlay(Capsule().stroke(active ? Color.clear : AppStyle.secondary.opacity(0.4), lineWidth: 1))
            .opacity(!isEnabled ? 0.4 : configuration.isPressed ? 0.7 : 1)
    }
}

struct UnevenTutorShape: Shape {
    func path(in rect: CGRect) -> Path {
        let radius = min(24.0, rect.height / 2)
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + radius))
        path.addQuadCurve(to: CGPoint(x: rect.minX + radius, y: rect.minY), control: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - radius, y: rect.minY))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: rect.minY + radius), control: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY)); path.closeSubpath()
        return path
    }
}

struct HintActionStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    var progress: Double
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(AndroidText.labelLarge).lineLimit(1)
            .padding(.horizontal, 12).frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(enabled ? AppStyle.onSurface : AppStyle.onSurface.opacity(0.38))
            .background {
                GeometryReader { geometry in
                    AppStyle.primary.opacity(0.35).frame(width: geometry.size.width * progress)
                }.clipShape(Capsule())
            }
            .overlay(Capsule().stroke(AppStyle.secondary.opacity(0.4), lineWidth: 1))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Android's chain arrows stop short of digits and move aside so opposing branches stay distinct.
struct ChainArrows: View {
    let chain: ChainEvidence
    var hypothetical = true
    let rect: (Int, CGSize) -> CGRect
    var body: some View {
        Canvas { context, size in
            func arrow(_ from: Int, _ to: Int) {
                let a = rect(from, size), b = rect(to, size), unit = min(a.width, a.height)
                let dx = b.midX - a.midX, dy = b.midY - a.midY, length = hypot(dx, dy)
                guard length > unit * 0.78 else { return }
                let ux = dx / length, uy = dy / length
                let start = CGPoint(x: a.midX + ux * unit * 0.34 - uy * unit * 0.13, y: a.midY + uy * unit * 0.34 + ux * unit * 0.13)
                let end = CGPoint(x: b.midX - ux * unit * 0.34 - uy * unit * 0.13, y: b.midY - uy * unit * 0.34 + ux * unit * 0.13)
                let angle = atan2(dy, dx), head = unit * 0.2
                var path = Path(); path.move(to: start); path.addLine(to: end)
                context.stroke(path, with: .color(.white), style: StrokeStyle(lineWidth: unit * 0.09, lineCap: .round))
                path.move(to: CGPoint(x: end.x - head * cos(angle - 0.45), y: end.y - head * sin(angle - 0.45)))
                path.addLine(to: end); path.addLine(to: CGPoint(x: end.x - head * cos(angle + 0.45), y: end.y - head * sin(angle + 0.45)))
                context.stroke(path, with: .color(Color(red: 23/255, green: 107/255, blue: 80/255)), style: StrokeStyle(lineWidth: unit * 0.045, lineCap: .round))
            }
            let cells = Set(chain.links.map(\.index))
            for link in chain.links { if let from = link.from, cells.contains(from) { arrow(from, link.index) } }
            if chain.deadEnd.count == 1, let from = chain.deadEndFrom { arrow(from, chain.deadEnd[0]) }
        }.mask {
            GeometryReader { geometry in
                ZStack(alignment: .topLeading) {
                    Color.white
                    ForEach(Array(chain.links.enumerated()), id: \.offset) { offset, link in
                        let cell = rect(link.index,geometry.size), side = min(cell.width,cell.height)
                        Text(String(link.digit)).font(hypothetical ? AppStyle.puzzleFont(size: side*0.62,weight: .bold).italic() : AppStyle.puzzleFont(size: side*0.62,weight: .bold))
                            .frame(width: cell.width,height: cell.height).position(x: cell.midX,y: cell.midY).blendMode(.destinationOut)
                        Text(String(offset+1)).font(AppStyle.puzzleFont(size: side*0.26,weight: .bold))
                            .frame(width: cell.width-side*0.08,height: cell.height-side*0.08,alignment: .topLeading)
                            .position(x: cell.midX,y: cell.midY).blendMode(.destinationOut)
                    }
                }.compositingGroup()
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

struct ChainContradiction: View {
    let chain: ChainEvidence
    let index: Int
    let side: CGFloat
    var body: some View {
        ZStack {
            if chain.deadEnd.count == 1, chain.deadEnd.contains(index) {
                Path { path in
                    path.move(to: CGPoint(x: side * 0.3, y: side * 0.3)); path.addLine(to: CGPoint(x: side * 0.7, y: side * 0.7))
                    path.move(to: CGPoint(x: side * 0.7, y: side * 0.3)); path.addLine(to: CGPoint(x: side * 0.3, y: side * 0.7))
                }.stroke(AppStyle.incorrect, style: StrokeStyle(lineWidth: side * 0.07, lineCap: .round))
            }
            if chain.blocked.contains(index), let missing = chain.missing {
                OutlinedDigit(digit: missing, side: side).frame(width: side, height: side)
            }
            if chain.blocked.contains(index) || (chain.deadEnd.count == 1 && chain.deadEnd.contains(index)) {
                Text(String(chain.links.count + 1)).font(AppStyle.puzzleFont(size: side * 0.26, weight: .bold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(side * 0.04)
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// A stroked ghost preserves the photograph and pencil marks beneath a contradiction.
struct OutlinedDigit: UIViewRepresentable {
    let digit: Int
    let side: CGFloat
    func makeUIView(context: Context) -> UILabel { let label = UILabel(); label.textAlignment = .center; label.isAccessibilityElement = false; return label }
    func updateUIView(_ label: UILabel, context: Context) {
        label.attributedText = NSAttributedString(string: String(digit), attributes: [
            .font: UIFont(name:"Roboto-Bold",size:side * 0.62) ?? UIFont.boldSystemFont(ofSize:side * 0.62),
            .strokeColor: UIColor(red: 239/255, green: 83/255, blue: 80/255, alpha: 1),
            .strokeWidth: 100 * 0.045 / 0.62,
            .foregroundColor: UIColor.clear
        ])
    }
}

struct CausalArrow: Decodable { let target: Int; let support: [Int] }
struct CausalArrows: View {
    let arrows: [CausalArrow]
    let targets: [Int]
    let rect: (Int, CGSize) -> CGRect
    var body: some View {
        Canvas { context, size in
            let green = Color(red: 23/255, green: 107/255, blue: 80/255)
            for target in targets {
                let cell = rect(target,size), unit = min(cell.width,cell.height)
                context.stroke(Path(cell.insetBy(dx: unit*0.1,dy: unit*0.1)),with: .color(green),lineWidth: unit*0.04)
            }
            for arrow in arrows where !arrow.support.isEmpty {
                let cell = rect(arrow.target,size), unit = min(cell.width,cell.height)
                let centers = arrow.support.map { rect($0,size) }
                let from = CGPoint(x: centers.map(\.midX).reduce(0,+)/CGFloat(centers.count),y: centers.map(\.midY).reduce(0,+)/CGFloat(centers.count))
                let dx = cell.midX-from.x, dy = cell.midY-from.y, length = hypot(dx,dy)
                guard length > unit*0.78 else { continue }
                let ux = dx/length, uy = dy/length
                let start = CGPoint(x: from.x+ux*unit*0.34-uy*unit*0.13,y: from.y+uy*unit*0.34+ux*unit*0.13)
                let end = CGPoint(x: cell.midX-ux*unit*0.34-uy*unit*0.13,y: cell.midY-uy*unit*0.34+ux*unit*0.13)
                var stem = Path(); stem.move(to: start); stem.addLine(to: end)
                context.stroke(stem,with: .color(.white),style: StrokeStyle(lineWidth: unit*0.09,lineCap: .round))
                let angle = atan2(dy,dx),head = unit*0.2
                var path = stem
                for turn in [-0.45,0.45] { path.move(to: end); path.addLine(to: CGPoint(x: end.x-head*cos(angle+turn),y: end.y-head*sin(angle+turn))) }
                context.stroke(path,with: .color(green),style: StrokeStyle(lineWidth: unit*0.045,lineCap: .round))
            }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
}

/// Android Material typography for app-owned text; native sheets keep system chrome.
enum AndroidText {
    static let body = Font.custom("Roboto-Regular",size:16,relativeTo:.body)
    static let bodyMedium = Font.custom("Roboto-Regular",size:14,relativeTo:.callout)
    static let bodySmall = Font.custom("Roboto-Regular",size:12,relativeTo:.caption)
    static let labelLarge = Font.custom("Roboto-Medium",size:14,relativeTo:.callout)
    static let labelSmall = Font.custom("Roboto-Medium",size:11,relativeTo:.caption2)
    static let titleMedium = Font.custom("Roboto-Medium",size:16,relativeTo:.headline)
    static let titleLarge = Font.custom("Roboto-Regular",size:22,relativeTo:.title2)
    static let headlineMedium = Font.custom("Roboto-Regular",size:28,relativeTo:.title)
    static let headlineLarge = Font.custom("Roboto-Regular",size:32,relativeTo:.largeTitle)
}

struct AndroidTextButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(AndroidText.labelLarge)
            .padding(.horizontal,12).padding(.vertical,8).frame(minHeight:48)
            .foregroundStyle(enabled ? AppStyle.primary : AppStyle.onSurface).opacity(enabled ? (configuration.isPressed ? 0.7 : 1) : 0.38)
    }
}

struct AndroidTonalButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(AndroidText.labelLarge).padding(.horizontal,24)
            .frame(maxWidth:.infinity,minHeight:40)
            .foregroundStyle(enabled ? Color(red:232/255,green:222/255,blue:248/255) : AppStyle.onSurface.opacity(0.38))
            .background(enabled ? Color(red:74/255,green:68/255,blue:88/255) : AppStyle.onSurface.opacity(0.12),in:Capsule())
            .padding(.vertical,4).opacity(configuration.isPressed ? 0.7 : 1)
    }
}
