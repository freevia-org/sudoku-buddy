import Foundation

/// A local-only handoff from the Share extension. No puzzle or feedback data lives here.
public struct SharedImageInbox {
    public static let group = "group.org.freevia.sudokubuddy.ios"
    public static let maximumBytes = 20 * 1024 * 1024
    public let directory: URL
    public init(container: URL) { directory = container.appendingPathComponent("SharedImages", isDirectory: true) }
    public enum InboxError: Error { case invalidSize, full, invalidName }

    public func enqueue(_ data: Data) throws {
        guard !data.isEmpty, data.count <= Self.maximumBytes else { throw InboxError.invalidSize }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        guard try pending().count < 5 else { throw InboxError.full }
        let url = directory.appendingPathComponent(UUID().uuidString + ".image")
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        var excluded = url; try excluded.setResourceValues(values)
    }

    /// Expired handoffs are removed; files are immutable once committed by atomic rename.
    public func pending(now: Date = Date()) throws -> [URL] {
        guard FileManager.default.fileExists(atPath: directory.path) else { return [] }
        let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.creationDateKey, .isRegularFileKey, .isSymbolicLinkKey])
        return try files.filter { url in
            guard url.pathExtension == "image", UUID(uuidString: url.deletingPathExtension().lastPathComponent) != nil else { return false }
            let values = try url.resourceValues(forKeys: [.creationDateKey, .isRegularFileKey, .isSymbolicLinkKey])
            guard values.isRegularFile == true, values.isSymbolicLink != true else { return false }
            if now.timeIntervalSince(values.creationDate ?? now) > 7 * 86400 { try FileManager.default.removeItem(at: url); return false }
            return true
        }.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    public func read(_ url: URL) throws -> Data {
        try validate(url)
        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size > 0, size <= Self.maximumBytes else { throw InboxError.invalidSize }
        return try Data(contentsOf: url)
    }
    public func remove(_ url: URL) throws { try validate(url); try FileManager.default.removeItem(at: url) }
    private func validate(_ url: URL) throws {
        guard url.deletingLastPathComponent().standardizedFileURL == directory.standardizedFileURL,
              url.pathExtension == "image", UUID(uuidString: url.deletingPathExtension().lastPathComponent) != nil,
              try url.resourceValues(forKeys: [.isSymbolicLinkKey]).isSymbolicLink != true else { throw InboxError.invalidName }
    }
}
