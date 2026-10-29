import UIKit
import UniformTypeIdentifiers
import ImageIO
import PuzzlePersistence

/// Accepts a single image, never URLs, text, or network content. Recognition runs in the app.
final class ShareViewController: UIViewController {
    private let language = ShareLanguage()
    private func text(_ source: String) -> String { language.text(source) }
    private let status = UILabel()
    private let save = UIButton(type: .system)
    private var imageData: Data?
    private var completed = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        view.semanticContentAttribute = language.isRTL ? .forceRightToLeft : .forceLeftToRight
        status.text = text("Opening photo…"); status.numberOfLines = 0
        save.setTitle(text("Keep"), for: .normal); save.isEnabled = false
        save.addTarget(self, action: #selector(storeImage), for: .touchUpInside)
        let cancel = UIButton(type: .system); cancel.setTitle(text("Cancel"), for: .normal)
        cancel.addTarget(self, action: #selector(cancelShare), for: .touchUpInside)
        let stack = UIStackView(arrangedSubviews: [status, save, cancel]); stack.axis = .vertical; stack.spacing = 24
        stack.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24), stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24), stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)])
        let attachments = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        guard attachments.count == 1, let provider = attachments.first,
              provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) else { status.text = text("Choose a photo") + " (1)"; return }
        provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { [weak self] url, error in
            // The provider's temporary file is valid only during this callback.
            let data: Data? = url.flatMap { url in
                guard let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize,
                      size > 0, size <= SharedImageInbox.maximumBytes,
                      let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                      let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
                      let width = properties[kCGImagePropertyPixelWidth] as? Int,
                      let height = properties[kCGImagePropertyPixelHeight] as? Int,
                      width > 0, height > 0, Double(width) * Double(height) <= 40_000_000 else { return nil }
                return try? Data(contentsOf: url)
            }
            DispatchQueue.main.async {
                guard let self, !self.completed else { return }
                self.imageData = data; self.save.isEnabled = data != nil
                self.status.text = data == nil ? self.text("That photo could not be opened. Try another photo.") + "\n≤20 MiB · ≤40 MP" : self.text("Keep") + " → Sudoku Buddy → " + self.text("Read") + "\n\n" + self.text("Photos are processed on this device. Review the reading before starting.") + "\n\n⏱ 7 × 24 h"
            }
        }
    }
    @objc private func storeImage() {
        guard !completed, let imageData else { return }
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: SharedImageInbox.group) else { status.text = text("Retry saving") + "\nSudoku Buddy → " + text("Shared images"); return }
        do {
            try SharedImageInbox(container: container).enqueue(imageData)
            self.imageData = nil; save.isHidden = true
            status.text = text("Sudoku Buddy") + " → " + text("Read")
            completed = true
            extensionContext?.completeRequest(returningItems: nil)
        } catch { status.text = text("Retry saving") + "\nSudoku Buddy → " + text("Shared images") + " → " + text("Delete this photo") }
    }
    @objc private func cancelShare() {
        completed = true; imageData = nil
        extensionContext?.cancelRequest(withError: NSError(domain: NSCocoaErrorDomain, code: NSUserCancelledError))
    }
}
