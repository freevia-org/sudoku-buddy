import UIKit
import UniformTypeIdentifiers
import ImageIO
import PuzzlePersistence

/// Accepts a single image, never URLs, text, or network content. Recognition runs in the app.
final class ShareViewController: UIViewController {
    private let status = UILabel()
    private let save = UIButton(type: .system)
    private var imageData: Data?
    private var completed = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        status.text = "Loading image…"; status.numberOfLines = 0
        save.setTitle("Save for Sudoku Buddy", for: .normal); save.isEnabled = false
        save.addTarget(self, action: #selector(storeImage), for: .touchUpInside)
        let cancel = UIButton(type: .system); cancel.setTitle("Cancel", for: .normal)
        cancel.addTarget(self, action: #selector(cancelShare), for: .touchUpInside)
        let stack = UIStackView(arrangedSubviews: [status, save, cancel]); stack.axis = .vertical; stack.spacing = 24
        stack.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24), stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24), stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)])
        let attachments = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        guard attachments.count == 1, let provider = attachments.first,
              provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) else { status.text = "Share one Sudoku image at a time."; return }
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
                self.status.text = data == nil ? "This image could not be read. Choose an image under 20 MB and 40 megapixels." : "Save this image on this device, then open Sudoku Buddy to read it. No image is uploaded by this extension."
            }
        }
    }
    @objc private func storeImage() {
        guard !completed, let imageData else { return }
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: SharedImageInbox.group) else { status.text = "Shared storage is unavailable. Open Sudoku Buddy and try again."; return }
        do {
            try SharedImageInbox(container: container).enqueue(imageData)
            self.imageData = nil; save.isHidden = true
            status.text = "Saved. Open Sudoku Buddy to read your image."
            completed = true
            extensionContext?.completeRequest(returningItems: nil)
        } catch { status.text = "Could not save the image. Open Sudoku Buddy to read or discard pending images, then try again." }
    }
    @objc private func cancelShare() {
        completed = true; imageData = nil
        extensionContext?.cancelRequest(withError: NSError(domain: NSCocoaErrorDomain, code: NSUserCancelledError))
    }
}
