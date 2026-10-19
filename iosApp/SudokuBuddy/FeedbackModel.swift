import Foundation
import Combine
import PuzzlePersistence

@MainActor
final class FeedbackModel: ObservableObject {
    static let shared = FeedbackModel()
    @Published private(set) var receipts: [SubmissionReceipt] = []
    @Published private(set) var busy = false
    @Published var notice: String?
    let endpoint: URL?
    private let store: ReceiptStore
    private let client: ReportClient
    private var queued: [UUID: ReportSnapshot] = [:]
    private var automaticTask: Task<Void, Never>?

    private init() {
        let args = ProcessInfo.processInfo.arguments
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var directory = support.appendingPathComponent("SudokuBuddyFeedback", isDirectory: true)
        var selected = (Bundle.main.object(forInfoDictionaryKey: "SudokuFeedbackEndpoint") as? String).flatMap(URL.init(string:))
        var transport: any ReportTransport = HTTPSReportTransport()
        #if DEBUG
        if let flag = args.firstIndex(of: "--history-test-session"), args.indices.contains(flag + 1), let id = UUID(uuidString: args[flag + 1]) {
            directory = support.appendingPathComponent("UITestFeedback/\(id.uuidString)", isDirectory: true)
        }
        if args.contains("--mock-feedback-service") {
            selected = URL(string: "https://feedback.example.test/v1/reports")
            transport = MockReportTransport()
        }
        #endif
        endpoint = selected.flatMap { ReportClient.validEndpoint($0) ? $0 : nil }
        store = ReceiptStore(directory: directory)
        client = ReportClient(transport: transport)
        reload()
    }
    func reload() {
        do { receipts = try store.list(); if !store.warnings.isEmpty { notice = store.warnings.joined(separator: " ") } }
        catch { notice = "Submission receipts could not be opened. Their files have been kept." }
    }
    func submit(_ snapshot: ReportSnapshot, consent: Bool, automatic: Bool = false) async {
        guard consent, !busy else { return }
        guard let endpoint else { notice = FeedbackError.unavailable.localizedDescription; return }
        busy = true; notice = nil
        defer { busy = false; reload() }
        do {
            let data = try await Task.detached(priority: .utility) { try ReportArchive.package(snapshot) }.value
            try Task.checkCancellation()
            if automatic && !AppSettings.shared.automaticSharing { return }
            let receipt = try store.stage(body: data, endpoint: endpoint, puzzleID: snapshot.puzzleID, automatic: automatic, consent: consent)
            reload()
            if receipt.state == .deleted || receipt.deletionRequested { return }
            if receipt.state == .accepted { notice = "Reading submitted. Thank you!"; return }
            try await send(receipt)
        } catch { notice = error.localizedDescription }
    }
    private func send(_ receipt: SubmissionReceipt) async throws {
        let archive = try store.archive(receipt)
        try await client.upload(receipt, body: archive)
        var confirmed = receipt; confirmed.state = .accepted; confirmed.confirmedAt = Date()
        try store.save(confirmed) // A pending receipt was durable before any bytes were sent.
        try? store.discardArchive(confirmed)
        notice = "Reading submitted. Thank you!"
    }
    func retry(_ receipt: SubmissionReceipt) async {
        guard !busy, receipt.state == .pending, !receipt.deletionRequested else { return }
        busy = true; notice = nil
        defer { busy = false; reload() }
        do { try await send(receipt) } catch { notice = error.localizedDescription }
    }
    func delete(_ receipt: SubmissionReceipt) async {
        guard !busy else { return }
        busy = true; notice = nil
        queued.removeValue(forKey: receipt.puzzleID)
        defer { busy = false; reload() }
        do {
            var deleting = receipt; deleting.deletionRequested = true
            try store.save(deleting) // Never resume a POST after the user requested deletion.
            try await client.delete(deleting)
            deleting.state = .deleted; deleting.confirmedAt = Date()
            try store.save(deleting)
            try? store.discardArchive(deleting)
        } catch { notice = "Deletion could not be confirmed. Check your connection and try again." }
    }
    func offerAutomatic(_ snapshot: ReportSnapshot) {
        guard AppSettings.shared.automaticSharing, endpoint != nil, !snapshot.evidence.uncertainCells.isEmpty else { return }
        queued[snapshot.puzzleID] = snapshot
        automaticTask?.cancel()
        automaticTask = Task {
            do { try await Task.sleep(nanoseconds: 1_500_000_000) } catch { return }
            while !Task.isCancelled, AppSettings.shared.automaticSharing, !queued.isEmpty {
                if busy { try? await Task.sleep(nanoseconds: 300_000_000); continue }
                guard let id = queued.keys.first, let next = queued.removeValue(forKey: id) else { break }
                await submit(next, consent: true, automatic: true)
            }
        }
    }
    func stopAutomatic() { automaticTask?.cancel(); automaticTask = nil; queued.removeAll() }
    func cancelQueued(_ id: UUID) { queued.removeValue(forKey: id) }
    func resumeAutomatic() async {
        guard AppSettings.shared.automaticSharing, !busy else { return }
        for receipt in receipts where receipt.automatic && receipt.state == .pending && !receipt.deletionRequested {
            guard AppSettings.shared.automaticSharing, !Task.isCancelled else { return }
            await retry(receipt)
        }
    }
}

#if DEBUG
private struct MockReportTransport: ReportTransport {
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let digest = request.httpMethod == "POST" ? ReportArchive.digest(request.httpBody ?? Data()) : request.url!.lastPathComponent
        let code = request.httpMethod == "POST" ? 201 : 200
        let status = request.httpMethod == "POST" ? "accepted" : "deleted"
        return (Data("{\"receipt\":\"\(digest)\",\"status\":\"\(status)\"}".utf8),
                HTTPURLResponse(url: request.url!, statusCode: code, httpVersion: nil, headerFields: nil)!)
    }
}
#endif
