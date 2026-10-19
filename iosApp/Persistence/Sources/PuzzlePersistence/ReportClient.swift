import Foundation

public struct SubmissionReceipt: Codable, Identifiable, Sendable, Equatable {
    public enum State: String, Codable, Sendable { case pending, accepted, deleted }
    public let id: String
    public let endpoint: URL
    public let puzzleID: UUID
    public let consentedAt: Date
    public let automatic: Bool
    public var state: State
    public var confirmedAt: Date?
    public var deletionRequested: Bool = false
    public let consentVersion: String
}

public final class ReceiptStore {
    public let directory: URL
    private let files = FileManager.default
    public private(set) var warnings: [String] = []
    public init(directory: URL) { self.directory = directory }
    public func list() throws -> [SubmissionReceipt] {
        guard files.fileExists(atPath: directory.path) else { return [] }
        warnings = []
        var receipts: [SubmissionReceipt] = []
        for url in try files.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) where url.pathExtension == "json" {
            do {
                let receipt = try JSONDecoder().decode(SubmissionReceipt.self, from: Data(contentsOf: url))
                guard ReportArchive.validDigest(receipt.id), receipt.id == url.deletingPathExtension().lastPathComponent,
                      ReportClient.validEndpoint(receipt.endpoint) else { throw FeedbackError.invalidResponse }
                receipts.append(receipt)
            } catch { warnings.append("Some submission receipts could not be opened. Their files have been kept.") }
        }
        return receipts.sorted { $0.consentedAt > $1.consentedAt }
    }
    public func stage(body: Data, endpoint: URL, puzzleID: UUID, automatic: Bool, consent: Bool) throws -> SubmissionReceipt {
        guard consent else { throw FeedbackError.missingConsent }
        guard ReportClient.validEndpoint(endpoint) else { throw FeedbackError.unavailable }
        guard body.count <= ReportArchive.maximumBytes, body.starts(with: [0x50, 0x4b, 0x03, 0x04]) else { throw FeedbackError.invalidReport }
        let digest = ReportArchive.digest(body)
        // Keep completed/deleted identities to avoid resurrecting a deleted revision.
        if let known = try list().first(where: { $0.id == digest }) {
            guard known.endpoint == endpoint else { throw FeedbackError.invalidResponse }
            return known
        }
        guard !files.fileExists(atPath: directory.appendingPathComponent(digest + ".json").path) else { throw FeedbackError.invalidResponse }
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        try body.write(to: archiveURL(digest), options: .atomic)
        let receipt = SubmissionReceipt(id: digest, endpoint: endpoint, puzzleID: puzzleID,
                                        consentedAt: Date(), automatic: automatic, state: .pending,
                                        consentVersion: "2026-10-10-analysis-training")
        try save(receipt)
        return receipt
    }
    public func save(_ receipt: SubmissionReceipt) throws {
        guard ReportArchive.validDigest(receipt.id), ReportClient.validEndpoint(receipt.endpoint) else { throw FeedbackError.invalidResponse }
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(receipt).write(to: directory.appendingPathComponent(receipt.id + ".json"), options: .atomic)
    }
    public func archive(_ receipt: SubmissionReceipt) throws -> Data {
        let data = try Data(contentsOf: archiveURL(receipt.id))
        guard data.count <= ReportArchive.maximumBytes, ReportArchive.digest(data) == receipt.id else { throw FeedbackError.invalidReport }
        return data
    }
    public func discardArchive(_ receipt: SubmissionReceipt) throws {
        let url = archiveURL(receipt.id)
        if files.fileExists(atPath: url.path) { try files.removeItem(at: url) }
    }
    private func archiveURL(_ digest: String) -> URL { directory.appendingPathComponent(digest + ".zip") }
}

public protocol ReportTransport: Sendable {
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

/// HTTPS only, no redirects/cookies/cache. Private archives never follow another origin.
public final class HTTPSReportTransport: NSObject, ReportTransport, URLSessionTaskDelegate, @unchecked Sendable {
    public override init() { super.init() }
    public func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                           newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
    public func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        guard request.url.map(ReportClient.validEndpointOrReceipt) == true else { throw FeedbackError.unavailable }
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.urlCache = nil
        config.timeoutIntervalForRequest = 20; config.timeoutIntervalForResource = 45
        let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (bytes, response) = try await session.bytes(for: request)
        guard let response = response as? HTTPURLResponse,
              response.expectedContentLength <= 65536 else { throw FeedbackError.invalidResponse }
        var data = Data()
        for try await byte in bytes {
            guard data.count < 65536 else { throw FeedbackError.invalidResponse }
            data.append(byte)
        }
        return (data, response)
    }
}

public struct ReportClient: Sendable {
    private let transport: any ReportTransport
    public init(transport: any ReportTransport = HTTPSReportTransport()) { self.transport = transport }
    public static func validEndpoint(_ url: URL) -> Bool {
        url.scheme == "https" && !(url.host ?? "").isEmpty && url.user == nil && url.password == nil &&
        url.query == nil && url.fragment == nil && url.path == "/v1/reports"
    }
    static func validEndpointOrReceipt(_ url: URL) -> Bool {
        validEndpoint(url) || (ReportArchive.validDigest(url.lastPathComponent) && validEndpoint(url.deletingLastPathComponent()))
    }
    public func upload(_ receipt: SubmissionReceipt, body: Data) async throws {
        guard receipt.state == .pending, !receipt.deletionRequested, ReportArchive.digest(body) == receipt.id,
              body.count <= ReportArchive.maximumBytes, Self.validEndpoint(receipt.endpoint) else { throw FeedbackError.invalidReport }
        var request = URLRequest(url: receipt.endpoint)
        request.httpMethod = "POST"; request.httpBody = body
        request.setValue("application/zip", forHTTPHeaderField: "Content-Type")
        request.setValue(String(body.count), forHTTPHeaderField: "Content-Length")
        request.setValue("yes", forHTTPHeaderField: "X-Sudoku-Training-Consent")
        let (data, response) = try await transport.send(request)
        try Self.validateUpload(code: response.statusCode, body: data, expected: receipt.id)
    }
    public func delete(_ receipt: SubmissionReceipt) async throws {
        guard Self.validEndpoint(receipt.endpoint), ReportArchive.validDigest(receipt.id) else { throw FeedbackError.invalidResponse }
        var request = URLRequest(url: receipt.endpoint.appendingPathComponent(receipt.id))
        request.httpMethod = "DELETE"
        let (body, response) = try await transport.send(request)
        try Self.validateDeletion(code: response.statusCode, body: body, expected: receipt.id)
    }
    public static func validateUpload(code: Int, body: Data, expected: String) throws {
        struct Reply: Decodable { let receipt: String; let status: String }
        guard code == 200 || code == 201 else { throw FeedbackError.http(code) }
        guard body.count <= 65536, let reply = try? JSONDecoder().decode(Reply.self, from: body),
              ReportArchive.validDigest(expected), reply.receipt == expected,
              (code == 201 && reply.status == "accepted") || (code == 200 && reply.status == "already_received") else { throw FeedbackError.invalidResponse }
    }
    public static func validateDeletion(code: Int, body: Data, expected: String) throws {
        struct Reply: Decodable { let receipt: String; let status: String }
        guard code == 200 else { throw FeedbackError.http(code) }
        guard body.count <= 65536, let reply = try? JSONDecoder().decode(Reply.self, from: body),
              ReportArchive.validDigest(expected), reply.receipt == expected,
              ["deleted", "already_deleted"].contains(reply.status) else { throw FeedbackError.deleted }
    }
}
