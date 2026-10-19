import Foundation

public struct RefusedScan: Codable, Identifiable, Equatable, Sendable {
    public let id: UUID
    public let at: Date
    public let reason: String
}

/// Six local original captures for explicit user-directed diagnostics sharing only.
public struct RefusedScanStore: Sendable {
    public let directory: URL
    public init(directory: URL) { self.directory = directory }
    public func list() -> [RefusedScan] {
        ((try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? [])
            .filter { $0.pathExtension == "json" }
            .compactMap { url -> RefusedScan? in
                guard let data = try? Data(contentsOf: url), let scan = try? JSONDecoder().decode(RefusedScan.self, from: data),
                      url.deletingPathExtension().lastPathComponent == scan.id.uuidString,
                      FileManager.default.fileExists(atPath: photoURL(scan.id).path) else { return nil }
                return scan
            }.sorted { $0.at > $1.at }
    }
    @discardableResult public func keep(_ bytes: Data, reason: String, at: Date = Date()) throws -> RefusedScan {
        guard !bytes.isEmpty, bytes.count <= 80 * 1024 * 1024 else { throw PuzzleStoreError.invalidRecord }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let scan = RefusedScan(id: UUID(), at: at, reason: String(reason.prefix(400)))
        try bytes.write(to: photoURL(scan.id), options: .atomic)
        do { try JSONEncoder().encode(scan).write(to: recordURL(scan.id), options: .atomic) }
        catch { try? FileManager.default.removeItem(at: photoURL(scan.id)); throw error }
        for old in list().dropFirst(6) { try discard(old) }
        return scan
    }
    public func photo(_ scan: RefusedScan) throws -> Data { try Data(contentsOf: photoURL(scan.id)) }
    public func discard(_ scan: RefusedScan) throws {
        for url in [photoURL(scan.id), recordURL(scan.id)] where FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }
    }
    private func photoURL(_ id: UUID) -> URL { directory.appendingPathComponent(id.uuidString + ".photo") }
    private func recordURL(_ id: UUID) -> URL { directory.appendingPathComponent(id.uuidString + ".json") }
}
