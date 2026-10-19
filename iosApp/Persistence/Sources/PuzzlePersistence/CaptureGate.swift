import CoreGraphics

/// Platform aspect-fill mapping. Capture decisions live in shared Kotlin CapturePolicy.
public enum CaptureGate {
    public static func onScreen(_ point: CGPoint, frame: CGSize, preview: CGSize) -> CGPoint {
        guard frame.width > 0, frame.height > 0 else { return .zero }
        let scale = max(preview.width / frame.width, preview.height / frame.height)
        return CGPoint(x: (preview.width - frame.width * scale) / 2 + point.x * frame.width * scale,
                       y: (preview.height - frame.height * scale) / 2 + point.y * frame.height * scale)
    }
}
